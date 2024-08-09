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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.daos.ExperimentalCollaboratorDAO;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.Role;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class ExperimentSecuredAnnotationRule extends SecuredAnnotationRule {

    // Executes before the SecuredAnnotationRule, and if the annotation exists, will return before the SecuredAnnotationRule can execute
    public static final Integer ORDER = SecuredAnnotationRule.ORDER - 1;

    public ExperimentSecuredAnnotationRule(RolesFinder rolesFinder) {
        super(rolesFinder);
    }

    @Inject
    private SecurityService securityService;
    @Inject
    private ProgramDAO programDAO;
    @Inject
    private BrAPITrialDAO brAPITrialDAO;
    @Inject
    private ExperimentalCollaboratorDAO experimentalCollaboratorDAO;

    @Override
    public SecurityRuleResult check(HttpRequest<?> request, @Nullable RouteMatch<?> routeMatch, @Nullable Map<String, Object> claims) {
        // Does not approve request so that checks after it can check. Only rejects on fail.

        if (routeMatch instanceof MethodBasedRouteMatch) {
            MethodBasedRouteMatch methodRoute = ((MethodBasedRouteMatch) routeMatch);
            String programId = (String) routeMatch.getVariableValues()
                    .get("programId");
            String experimentId = (String) routeMatch.getVariableValues()
                    .get("trialId");

            if (methodRoute.hasAnnotation(ExperimentSecured.class)) {
                if (programId == null) {
                    throw new HttpServerException("Endpoint does not have program id to check roles against");
                }
                if (experimentId == null) {
                    throw new HttpServerException("Endpoint does not have experiment id to check roles against");
                }

                if (!programDAO.existsById(UUID.fromString(programId))) {
                    throw new HttpStatusException(HttpStatus.NOT_FOUND, "Program does not exist");
                }
                Optional<BrAPITrial> trial = null;
                try {
                    trial = brAPITrialDAO.getTrialById(UUID.fromString(programId), UUID.fromString(experimentId));
                } catch (ApiException e) {
                    throw new RuntimeException(e);
                } catch (DoesNotExistException e) {
                    throw new HttpStatusException(HttpStatus.NOT_FOUND, "Experiment does not exist");
                }
                if( trial.isEmpty()) {
                    throw new HttpStatusException(HttpStatus.NOT_FOUND, "Experiment does not exist");
                }

                if (claims != null){
                    AuthenticatedUser user = securityService.getUser();

                    return processExperiment(user, experimentId, programId);
                }

                // Rejects if no claims
                return SecurityRuleResult.REJECTED;
            }
        }

        return SecurityRuleResult.UNKNOWN;
    }

    private SecurityRuleResult processExperiment(AuthenticatedUser authenticatedUser, String experimentId, String programId) {
        ProgramUser programUser = authenticatedUser.extractProgramUser(UUID.fromString(programId));
        if(this.isExperimentCoordinator(programUser)){
            List<UUID> colaboratableExperimentIds = experimentalCollaboratorDAO.fetchExperimentIds(authenticatedUser.getId(), UUID.fromString(programId));
            if(colaboratableExperimentIds.contains( UUID.fromString(experimentId)) ){
                return SecurityRuleResult.ALLOWED;
            }
        }
        return SecurityRuleResult.REJECTED;
    }

    private boolean isExperimentCoordinator(ProgramUser programUser){
        List<Role> roles = programUser.getRoles();
        return (roles.size()==1 &&
                ProgramSecuredRole.getEnum(roles.get(0).getDomain())==ProgramSecuredRole.EXPERIMENTAL_COLLABORATOR);

    }

    @Override
    public int getOrder() {
        return ORDER;
    }

}