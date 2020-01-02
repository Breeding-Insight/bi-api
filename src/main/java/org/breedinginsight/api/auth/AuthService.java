package org.breedinginsight.api.auth;

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
public class AuthService extends JwtCookieLoginHandler {

    public AuthService(JwtCookieConfiguration jwtCookieConfiguration,
                       JwtGeneratorConfiguration jwtGeneratorConfiguration,
                       AccessRefreshTokenGenerator accessRefreshTokenGenerator) {
        super(jwtCookieConfiguration, jwtGeneratorConfiguration, accessRefreshTokenGenerator);
    }

    @Override
    public HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
        System.out.println("loginSuccess: User = " + userDetails.getUsername());
        HttpResponse response = super.loginSuccess(userDetails, request);
        return response;
    }

    @Override
    public HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
        System.out.println("loginFailed");
        return super.loginFailed(authenticationFailed);
    }
}