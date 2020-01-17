package org.breedinginsight.api.auth;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.token.jwt.cookie.JwtCookieClearerLogoutHandler;
import io.micronaut.security.token.jwt.cookie.JwtCookieConfiguration;

import javax.inject.Singleton;

@Replaces(JwtCookieClearerLogoutHandler.class)
@Singleton
public class AuthServiceLogoutHandler extends JwtCookieClearerLogoutHandler {

    public AuthServiceLogoutHandler(JwtCookieConfiguration jwtCookieConfiguration) {
        super(jwtCookieConfiguration);
    }

    @Override
    public HttpResponse logout(HttpRequest<?> request) {
        // If we want to hook code into the logout process in the future we can put it here, for now just
        // passes through to JwtCookieClearerLogoutHandler
        return super.logout(request);
    }
}