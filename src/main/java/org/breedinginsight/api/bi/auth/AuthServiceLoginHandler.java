package org.breedinginsight.api.bi.auth;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.token.jwt.cookie.JwtCookieConfiguration;
import io.micronaut.security.token.jwt.cookie.JwtCookieLoginHandler;
import io.micronaut.security.token.jwt.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.jwt.generator.JwtGeneratorConfiguration;

import javax.inject.Singleton;

@Replaces(JwtCookieLoginHandler.class)
@Singleton
public class AuthServiceLoginHandler extends JwtCookieLoginHandler {

    public AuthServiceLoginHandler(JwtCookieConfiguration jwtCookieConfiguration,
                       JwtGeneratorConfiguration jwtGeneratorConfiguration,
                       AccessRefreshTokenGenerator accessRefreshTokenGenerator) {
        super(jwtCookieConfiguration, jwtGeneratorConfiguration, accessRefreshTokenGenerator);
    }

    @Override
    public HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
        // If we want to hook code into the login process in the future we can put it here, for now just
        // passes through to JwtCookieLoginHandler
        return super.loginSuccess(userDetails, request);
    }

    @Override
    public HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
        // If we want to hook code into the login process in the future we can put it here, for now just
        // passes through to JwtCookieLoginHandler
        return super.loginFailed(authenticationFailed);
    }
}