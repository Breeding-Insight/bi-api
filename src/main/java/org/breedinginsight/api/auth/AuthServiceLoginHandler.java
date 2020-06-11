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

package org.breedinginsight.api.auth;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.cookie.Cookie;
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
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Replaces(JwtCookieLoginHandler.class)
@Singleton
public class AuthServiceLoginHandler extends JwtCookieLoginHandler {

    @Inject
    private UserService userService;
    private String loginSuccessUrlCookieName = "redirect-login";

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

        AuthenticationFailed authenticationFailed = new AuthenticationFailed(AuthenticationFailureReason.UNKNOWN);
        return super.loginFailed(authenticationFailed);
    }

    @Override
    protected HttpResponse loginSuccessWithCookies(List<Cookie> cookies) {
        try {
            String locationUrl = this.jwtCookieConfiguration.getLoginSuccessTargetUrl();

            Optional<HttpRequest<Object>> requestOptional = ServerRequestContext.currentRequest();
            if (requestOptional.isPresent()){
                HttpRequest<Object> request = requestOptional.get();
                if (request.getCookies().contains(loginSuccessUrlCookieName)){
                    Cookie loginSuccessCookie = request.getCookies().get(loginSuccessUrlCookieName);
                    String returnUrl = loginSuccessCookie.getValue();
                    if (isValidURI(returnUrl)){
                        try {
                            locationUrl = URLDecoder.decode(returnUrl, StandardCharsets.UTF_8.name());
                        } catch (UnsupportedEncodingException e){}
                    }
                }
            }

            URI location = new URI(locationUrl).normalize();

            MutableHttpResponse mutableHttpResponse = HttpResponse.seeOther(location);

            Cookie cookie;
            for(Iterator var4 = cookies.iterator(); var4.hasNext(); mutableHttpResponse = mutableHttpResponse.cookie(cookie)) {
                cookie = (Cookie)var4.next();
            }

            return mutableHttpResponse;
        } catch (URISyntaxException var6) {
            return HttpResponse.serverError();
        }
    }

    @Override
    public HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
        // If we want to hook code into the login process in the future we can put it here, for now just
        // passes through to JwtCookieLoginHandler
        return super.loginFailed(authenticationFailed);
    }

    private Boolean isValidURI(String url) {
        try {
            new URI(url);
            return true;
        } catch (URISyntaxException e){
            return false;
        }
    }
}