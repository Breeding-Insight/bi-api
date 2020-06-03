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

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.AbstractSecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.token.RolesFinder;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import org.breedinginsight.model.User;
import org.breedinginsight.services.UserService;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class ValidUserSecurityRule extends AbstractSecurityRule {

    // Excecutes before the ProgramSecuredAnnotationRule, and if the annotation exists, will return before the SecuredAnnotationRule can execute
    public static final Integer ORDER = ProgramSecuredAnnotationRule.ORDER - 1;

    @Inject
    UserService userService;

    public ValidUserSecurityRule(RolesFinder rolesFinder) {
        super(rolesFinder);
    }

    @Override
    public SecurityRuleResult check(HttpRequest request, @Nullable RouteMatch routeMatch, @Nullable Map<String, Object> claims) {

        // Check user if the route is secured
        if (routeMatch instanceof MethodBasedRouteMatch) {
            MethodBasedRouteMatch methodRoute = ((MethodBasedRouteMatch) routeMatch);
            if (methodRoute.hasAnnotation(Secured.class) && request.getMethod() != HttpMethod.GET) {
                if (claims != null){
                    String requestId = (String) claims.get("id");
                    if (requestId != null) {
                        //TODO: Test that this IllegalArgumentException is caught by InternalServerHandler
                        UUID id = UUID.fromString(requestId);
                        Optional<User> user = userService.getById(id);
                        if (user.isPresent()){
                            if (user.get().getActive()) {
                                // They are ok, resume processing to next security rules
                                return SecurityRuleResult.UNKNOWN;
                            }
                        }
                    }
                }
                // Reject if no claims, no id in claims, user doesn't exist, or user is inactive
                return SecurityRuleResult.REJECTED;
            }
        }

        return SecurityRuleResult.UNKNOWN;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
