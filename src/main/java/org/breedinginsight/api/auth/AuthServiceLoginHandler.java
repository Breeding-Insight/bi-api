package org.breedinginsight.api.auth;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.token.jwt.cookie.JwtCookieConfiguration;
import io.micronaut.security.token.jwt.cookie.JwtCookieLoginHandler;
import io.micronaut.security.token.jwt.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.jwt.generator.JwtGeneratorConfiguration;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.breedinginsight.services.UserService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Replaces(JwtCookieLoginHandler.class)
@Singleton
public class AuthServiceLoginHandler extends JwtCookieLoginHandler {

    @Inject
    private UserService userService;

    public AuthServiceLoginHandler(JwtCookieConfiguration jwtCookieConfiguration,
                       JwtGeneratorConfiguration jwtGeneratorConfiguration,
                       AccessRefreshTokenGenerator accessRefreshTokenGenerator) {
        super(jwtCookieConfiguration, jwtGeneratorConfiguration, accessRefreshTokenGenerator);
    }

    @Override
    public HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
        // Called when login to orcid is successful.
        // Check if our login to our system is successful.

        Optional<User> user = userService.getByOrcid(userDetails.getUsername());

        if (user.isPresent()) {
            if (user.get().getActive()) {
                List<SystemRole> systemRoles = user.get().getSystemRoles();
                List<String> systemRoleStrings = systemRoles.stream().map((systemRole -> {
                    return systemRole.getDomain().toUpperCase();
                })).collect(Collectors.toList());

                //TODO: Get the program roles

                AuthenticatedUser authenticatedUser = new AuthenticatedUser(userDetails.getUsername(), systemRoleStrings, user.get().getId());
                return super.loginSuccess(authenticatedUser, request);
            }
        }

        AuthenticationFailed authenticationFailed = new AuthenticationFailed(AuthenticationFailureReason.USER_NOT_FOUND);
        return super.loginFailed(authenticationFailed);
    }

    @Override
    public HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
        // If we want to hook code into the login process in the future we can put it here, for now just
        // passes through to JwtCookieLoginHandler
        return super.loginFailed(authenticationFailed);
    }
}