package org.breedinginsight.api.auth;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpHeaders;
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
        System.out.println("logout");
        HttpResponse response = super.logout(request);
        HttpHeaders headers = response.getHeaders();
        return response;
    }

}