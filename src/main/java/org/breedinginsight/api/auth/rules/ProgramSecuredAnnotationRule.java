/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.api.auth.rules;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.security.rules.SecuredAnnotationRule;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.token.RolesFinder;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.ProgramUser;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class ProgramSecuredAnnotationRule extends SecuredAnnotationRule {

    // Excecutes before the SecuredAnnotationRule, and if the annotation exists, will return before the SecuredAnnotationRule can execute
    public static final Integer ORDER = SecuredAnnotationRule.ORDER - 1;

    public ProgramSecuredAnnotationRule(RolesFinder rolesFinder) {
        super(rolesFinder);
    }

    @Inject
    private SecurityService securityService;
    @Inject
    private ProgramDAO programDAO;

    @Override
    public SecurityRuleResult check(HttpRequest<?> request, @Nullable RouteMatch<?> routeMatch, @Nullable Map<String, Object> claims) {
        // Does not approve request so that checks after it can check. Only rejects on fail.

        if (routeMatch instanceof MethodBasedRouteMatch) {
            MethodBasedRouteMatch methodRoute = ((MethodBasedRouteMatch) routeMatch);

            String programId = (String) routeMatch.getVariableValues()
                    .get("programId");

            if (methodRoute.hasAnnotation(ProgramSecured.class)) {
                if (programId == null) {
                    throw new HttpServerException("Endpoint does not have program id to check roles against");
                }

                if (!programDAO.existsById(UUID.fromString(programId))) {
                    throw new HttpStatusException(HttpStatus.NOT_FOUND, "Program does not exist");
                }

                if (claims != null){
                    AuthenticatedUser user = securityService.getUser();
                    List<ProgramUser> allProgramRoles = user.getProgramRoles();
                    List<String> systemRoles = (List<String>) user.getRoles();

                    // Get program roles for given program and system roles into single list
                    List<ProgramSecuredRole> userRoles = processRoles(allProgramRoles, systemRoles, programId);

                    // Get route allowed roles
                    List<ProgramSecuredRole> allowedRoles = getAllowedRoles(methodRoute);

                    List<String> allowedRolesString = allowedRoles
                            .stream().map(ProgramSecuredRole::toString).collect(Collectors.toList());

                    List<String> userRolesString = userRoles.stream()
                            .map(ProgramSecuredRole::toString).collect(Collectors.toList());

                    SecurityRuleResult securityRuleResult = compareRoles(allowedRolesString, userRolesString);
                    return securityRuleResult;
                }

                // Rejects if no claims, or does not have correct roles
                return SecurityRuleResult.REJECTED;
            }
        }

        return SecurityRuleResult.UNKNOWN;
    }

    public List<ProgramSecuredRole> processRoles(List<ProgramUser> allProgramRoles, List<String> systemRoles, String programId) {

        // Check that they have a role in the program they are requesting data for
        List<ProgramUser> matchedProgramRoles = allProgramRoles.stream().filter(programRole ->
                programRole.getProgramId().toString().equals(programId) && programRole.getActive()).collect(Collectors.toList());

        // Get roles of the user for the given program
        List<ProgramSecuredRole> userRoles = new ArrayList<>();
        if (!matchedProgramRoles.isEmpty()){
            matchedProgramRoles.get(0).getRoles().stream()
                    .forEach(role -> userRoles.add(ProgramSecuredRole.getEnum(role.getDomain())));
        }

        // Add system roles to the user's roles. System roles apply to every program
        systemRoles.stream().forEach(systemRole -> userRoles.add(ProgramSecuredRole.getEnum(systemRole)));
        return userRoles;
    }

    public List<ProgramSecuredRole> getAllowedRoles(MethodBasedRouteMatch methodRoute) {

        Optional<ProgramSecuredRole[]> programSecuredRoles = methodRoute.getValue(ProgramSecured.class, "roles", ProgramSecuredRole[].class);
        Optional<ProgramSecuredRoleGroup[]> programSecuredRoleGroups = methodRoute.getValue(ProgramSecured.class, "roleGroups", ProgramSecuredRoleGroup[].class);
        List<ProgramSecuredRole> allowedRoles = new ArrayList<>();
        if (programSecuredRoles.isPresent()) {
            allowedRoles.addAll(Arrays.asList(programSecuredRoles.get()));
        }

        if (programSecuredRoleGroups.isPresent()) {
            Arrays.asList(programSecuredRoleGroups.get())
                    .stream().forEach(programSecuredRoleGroup -> allowedRoles.addAll(programSecuredRoleGroup.getProgramRoles()));
        }
        return allowedRoles;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}