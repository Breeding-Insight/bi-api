package org.breedinginsight.api.auth.rules;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.AbstractSecurityRule;
import io.micronaut.security.rules.SecuredAnnotationRule;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.token.RolesFinder;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import org.breedinginsight.services.UserService;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

public class ValidUserSecurityRule extends AbstractSecurityRule {

    @Inject
    UserService userService;
    // Comes before the security annotation rule
    public static final Integer ORDER = SecuredAnnotationRule.ORDER - 1;


    public ValidUserSecurityRule(RolesFinder rolesFinder) {
        super(rolesFinder);
    }

    @Override
    public SecurityRuleResult check(HttpRequest request, @Nullable RouteMatch routeMatch, @Nullable Map<String, Object> claims) {

        // Check user if the route is secured
        if (routeMatch instanceof MethodBasedRouteMatch) {
            MethodBasedRouteMatch methodRoute = ((MethodBasedRouteMatch) routeMatch);
            if (methodRoute.hasAnnotation(Secured.class)) {
                if (claims != null){
                    String requestId = (String) claims.get("id");
                    if (requestId != null) {
                        //TODO: Test that this IllegalArgumentException is caught by InternalServerHandler
                        UUID id = UUID.fromString(requestId);
                        if (userService.exists(id)) {
                            // They are ok, resume processing to next security rules
                            return SecurityRuleResult.UNKNOWN;
                        } else {
                            return SecurityRuleResult.REJECTED;
                        }
                    }
                }
            }
        }

        return SecurityRuleResult.UNKNOWN;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
