package org.breedinginsight.api.auth.rules;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.rules.SecuredAnnotationRule;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.token.RolesFinder;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import org.breedinginsight.api.auth.ProgramSecured;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class ProgramSecuredAnnotationRule extends SecuredAnnotationRule {

    // Excecutes before the SecuredAnnotationRule, and if the annotation exists, will return before the SecuredAnnotationRule can execute
    public static final Integer ORDER = SecuredAnnotationRule.ORDER - 1;

    public ProgramSecuredAnnotationRule(RolesFinder rolesFinder) {
        super(rolesFinder);
    }

    @Override
    public SecurityRuleResult check(HttpRequest request, @Nullable RouteMatch routeMatch, @Nullable Map<String, Object> claims) {
        // Does not approve request so that checks after it can check. Only rejects on fail.

        if (routeMatch instanceof MethodBasedRouteMatch) {
            MethodBasedRouteMatch methodRoute = ((MethodBasedRouteMatch) routeMatch);

            String programId = (String) routeMatch.getVariableValues()
                    .get("programId");
            if (methodRoute.hasAnnotation(ProgramSecured.class)) {

                if (claims != null){
                    Optional<String[]> programRoles = methodRoute.getValue(ProgramSecured.class, String[].class);
                    if (programRoles.isEmpty()) {
                        programRoles = Optional.of(new String[]{"MEMBER"});
                    }
                    SecurityRuleResult result = compareRoles(Arrays.asList(programRoles.get()), getUserProgramRoles(claims, programId));

                    if (result == SecurityRuleResult.ALLOWED){
                        return SecurityRuleResult.UNKNOWN;
                    }
                }

                // Rejects if no claims, or does not have correct roles
                return SecurityRuleResult.REJECTED;
            }
        }

        return SecurityRuleResult.UNKNOWN;
    }

    private List<String> getUserProgramRoles(Map<String, Object> claims, String programId) {
        //TODO: Write test for claims that does not have programRoles in it
        //TODO: Write test for a program id that does not have program id in it
        var userAllProgRoles = (Map<String, Object>) claims.get("programRoles");
        List<String> userProgRoles = (List<String>) userAllProgRoles.get(programId);

        //make sure they have the member role so they can access base program endpoints
        userProgRoles.add("MEMBER");

        return userProgRoles;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}