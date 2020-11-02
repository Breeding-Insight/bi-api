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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.authentication.AuthenticationException;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.token.jwt.cookie.JwtCookieConfiguration;
import io.micronaut.security.token.jwt.cookie.JwtCookieLoginHandler;
import io.micronaut.security.token.jwt.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.jwt.generator.JwtGeneratorConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.auth.SignUpJWT;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.breedinginsight.services.SignUpJwtService;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.JwtValidationException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Replaces(JwtCookieLoginHandler.class)
@Singleton
@Slf4j
public class AuthServiceLoginHandler extends JwtCookieLoginHandler {

    private String NEW_ACCOUNT_ERROR_ATTRIBUTE = "error";

    @Property(name = "web.cookies.login-redirect")
    private String loginSuccessUrlCookieName;
    @Property(name = "web.cookies.account-token")
    private String accountTokenCookieName;
    @Property(name = "web.login.error.url")
    private String loginErrorUrl;
    @Property(name = "web.signup.success.url")
    private String newAccountSuccessUrl;
    @Property(name = "web.signup.error.url")
    private String newAccountErrorUrl;

    @Inject
    private UserService userService;
    @Inject
    private SignUpJwtService signUpJwtService;

    public AuthServiceLoginHandler(JwtCookieConfiguration jwtCookieConfiguration,
                       JwtGeneratorConfiguration jwtGeneratorConfiguration,
                       AccessRefreshTokenGenerator accessRefreshTokenGenerator) {
        super(jwtCookieConfiguration, jwtGeneratorConfiguration, accessRefreshTokenGenerator);
    }

    @Override
    public HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
        // Called when login to orcid is successful.
        // Check if our login to our system is successful.
        if (request.getCookies().contains(accountTokenCookieName)) {
            Cookie accountTokenCookie = request.getCookies().get(accountTokenCookieName);
            String accountToken = accountTokenCookie.getValue();
            return newAccountCreationResponse(userDetails, accountToken, request);
        }

        // Normal login
        try {
            AuthenticatedUser authenticatedUser = getUserCredentials(userDetails);
            return super.loginSuccess(authenticatedUser, request);
        } catch (AuthenticationException e) {
            AuthenticationFailed authenticationFailed = new AuthenticationFailed(AuthenticationFailureReason.USER_NOT_FOUND);
            return loginFailed(authenticationFailed);
        }
    }

    private AuthenticatedUser getUserCredentials(UserDetails userDetails) throws AuthenticationException {

        Optional<User> user = userService.getByOrcid(userDetails.getUsername());

        if (user.isPresent()) {
            if (user.get().getActive()) {
                List<SystemRole> systemRoles = user.get().getSystemRoles();
                List<String> systemRoleStrings = systemRoles.stream().map((systemRole -> {
                    return systemRole.getDomain().toUpperCase();
                })).collect(Collectors.toList());

                //TODO: Get the program roles

                AuthenticatedUser authenticatedUser = new AuthenticatedUser(userDetails.getUsername(), systemRoleStrings, user.get().getId());
                return authenticatedUser;
            }
        }

        throw new AuthenticationException();
    }

    @Override
    protected HttpResponse loginSuccessWithCookies(List<Cookie> cookies) {
        try {
            String locationUrl = this.jwtCookieConfiguration.getLoginSuccessTargetUrl();

            Optional<HttpRequest<Object>> requestOptional = this.getCurrentRequest();
            if (requestOptional.isPresent()){
                HttpRequest<Object> request = requestOptional.get();
                if (request.getCookies().contains(loginSuccessUrlCookieName)){
                    Cookie loginSuccessCookie = request.getCookies().get(loginSuccessUrlCookieName);
                    String returnUrl = loginSuccessCookie.getValue();
                    try {
                        returnUrl = URLDecoder.decode(returnUrl, StandardCharsets.UTF_8.name());
                        if (isValidURL(returnUrl)){
                            locationUrl = returnUrl;
                        } else {
                            log.info("Invalid url: " + returnUrl);
                        }
                    } catch (UnsupportedEncodingException e){
                        log.info("Error decoding url: " + returnUrl);
                    }
                }
            }

            URI location = new URI(locationUrl);

            MutableHttpResponse mutableHttpResponse = HttpResponse.seeOther(location);

            Cookie cookie;
            for(Iterator cookieIterator = cookies.iterator(); cookieIterator.hasNext(); mutableHttpResponse = mutableHttpResponse.cookie(cookie)) {
                cookie = (Cookie)cookieIterator.next();
            }

            return mutableHttpResponse;
        } catch (URISyntaxException e) {
            log.error(e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Override
    public HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
        // If we want to hook code into the login process in the future we can put it here, for now just
        // passes through to JwtCookieLoginHandler

        try {
            URI location;
            if (authenticationFailed.getReason().equals(AuthenticationFailureReason.USER_NOT_FOUND)){
                location = new URI(jwtCookieConfiguration.getLoginFailureTargetUrl());
            } else {
                location = new URI(loginErrorUrl);
            }

            return HttpResponse.seeOther(location);
        } catch (URISyntaxException e) {
            return HttpResponse.serverError();
        }
    }

    private HttpResponse newAccountCreationResponse(UserDetails userDetails, String accountToken, HttpRequest request) {

        String orcid = userDetails.getUsername();
        SignUpJWT signUpJWT;
        try {
            signUpJWT = signUpJwtService.validateAndParseAccountSignUpJwt(accountToken);
        } catch (JwtValidationException e) {
            log.error(e.getMessage());
            // Return them to an error page
            HttpResponse resp = HttpResponse.seeOther(URI.create(newAccountErrorUrl));
            return resp;
        }

        // Query db and check that jwt id is valid
        Optional<User> user = userService.getById(signUpJWT.getUserId());
        if (!user.isPresent()) {
            HttpResponse resp = HttpResponse.seeOther(URI.create(newAccountErrorUrl));
            return resp;
        }
        User newUser = user.get();
        if (newUser.getAccountToken() == null){
            HttpResponse resp = HttpResponse.seeOther(URI.create(newAccountErrorUrl));
            return resp;
        }

        if (newUser.getAccountToken().equals(signUpJWT.getJwtId().toString())) {
            // Assign orcid to that user
            try {
                userService.updateOrcid(newUser.getId(), orcid);
            } catch (DoesNotExistException e) {
                HttpResponse resp = HttpResponse.seeOther(URI.create(newAccountErrorUrl));
                return resp;
            } catch (AlreadyExistsException e) {
                String url = String.format("%s?%s=409", newAccountErrorUrl, NEW_ACCOUNT_ERROR_ATTRIBUTE);
                HttpResponse resp = HttpResponse.seeOther(URI.create(url));
                return resp;
            }

            // Get logged in JWT and redirect user to account creation success page
            try {
                AuthenticatedUser authenticatedUser = getUserCredentials(userDetails);
                Optional<Cookie> cookieOptional = super.accessTokenCookie(authenticatedUser, request);
                if (!cookieOptional.isPresent()) {
                    return HttpResponse.seeOther(URI.create(newAccountErrorUrl));
                }
                Cookie cookie = cookieOptional.get();
                MutableHttpResponse resp = HttpResponse.seeOther(URI.create(newAccountSuccessUrl));
                resp.cookie(cookie);
                return resp;
            } catch (AuthenticationException e) {
                return HttpResponse.seeOther(URI.create(newAccountErrorUrl));
            }

        } else {
            // JWT ID did not match
            return HttpResponse.seeOther(URI.create(newAccountErrorUrl));
        }
    }

    private Boolean isValidURL(String urlString) {
        try {
            URL url = new URL(urlString);
            return "https".equals(url.getProtocol()) || "http".equals(url.getProtocol());
        } catch (MalformedURLException e){
            return false;
        }
    }

    public Optional<HttpRequest<Object>> getCurrentRequest() {
        return ServerRequestContext.currentRequest();
    }

}