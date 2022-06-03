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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.simple.cookies.SimpleCookies;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.breedinginsight.DatabaseTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AuthServiceLoginHandlerUnitTest extends DatabaseTest {

    @Inject
    private AuthServiceLoginHandler authServiceLoginHandler;

    @Property(name = "micronaut.security.token.jwt.cookie.login-success-target-url")
    private String defaultUrl;
    @Property(name = "web.cookies.login-redirect")
    private String loginRedirectCookieName;

    private String userName = "1111-2222-3333-4444";

    @AfterAll
    public void finish() {
        super.stopContainers();
    }

    @Test
    public void returnsDefaultBadUrl() {


        HttpRequest request = mock(HttpRequest.class, CALLS_REAL_METHODS);
        Cookie returnUrlCookie = Cookie.of(loginRedirectCookieName, "cats man...");
        SimpleCookies cookies = new SimpleCookies(ConversionService.SHARED);
        cookies.put("redirect-login", returnUrlCookie);
        doReturn(cookies).when(request).getCookies();
        doReturn(false).when(request).isSecure();
        AuthServiceLoginHandler authServiceSpy = spy(authServiceLoginHandler);
        when(authServiceSpy.getCurrentRequest()).thenReturn(Optional.of(request));

        UserDetails user = new UserDetails(userName, new ArrayList<>());

        HttpResponse response = authServiceSpy.loginSuccess(user, request);

        checkAssertions(response, defaultUrl);
    }

    @Test
    public void returnsPassedUrlGoodHttpUrl() {

        String expectedUrl = "http://localhost:8080/test?testparam=true";
        HttpRequest request = mock(HttpRequest.class, CALLS_REAL_METHODS);
        Cookie returnUrlCookie = Cookie.of(loginRedirectCookieName, expectedUrl);
        SimpleCookies cookies = new SimpleCookies(ConversionService.SHARED);
        cookies.put("redirect-login", returnUrlCookie);
        doReturn(cookies).when(request).getCookies();
        doReturn(false).when(request).isSecure();
        AuthServiceLoginHandler authServiceSpy = spy(authServiceLoginHandler);
        when(authServiceSpy.getCurrentRequest()).thenReturn(Optional.of(request));

        UserDetails user = new UserDetails(userName, new ArrayList<>());

        HttpResponse response = authServiceSpy.loginSuccess(user, request);

        reset(request);
        checkAssertions(response, expectedUrl);
    }

    @Test
    public void returnsPassedUrlGoodHttpsUrl() {

        String expectedUrl = "https://localhost:8080/test?testparam=blueberry";
        HttpRequest request = mock(HttpRequest.class, CALLS_REAL_METHODS);
        Cookie returnUrlCookie = Cookie.of(loginRedirectCookieName, expectedUrl);
        SimpleCookies cookies = new SimpleCookies(ConversionService.SHARED);
        cookies.put("redirect-login", returnUrlCookie);
        doReturn(cookies).when(request).getCookies();
        doReturn(false).when(request).isSecure();
        AuthServiceLoginHandler authServiceSpy = spy(authServiceLoginHandler);
        when(authServiceSpy.getCurrentRequest()).thenReturn(Optional.of(request));

        UserDetails user = new UserDetails(userName, new ArrayList<>());

        HttpResponse response = authServiceSpy.loginSuccess(user, request);

        reset(request);
        checkAssertions(response, expectedUrl);
    }

    @Test
    public void returnsDefaultUrlCookieNotExist() {

        HttpRequest request = mock(HttpRequest.class, CALLS_REAL_METHODS);
        SimpleCookies cookies = new SimpleCookies(ConversionService.SHARED);
        doReturn(cookies).when(request).getCookies();
        doReturn(false).when(request).isSecure();
        AuthServiceLoginHandler authServiceSpy = spy(authServiceLoginHandler);
        when(authServiceSpy.getCurrentRequest()).thenReturn(Optional.of(request));

        UserDetails user = new UserDetails(userName, new ArrayList<>());

        HttpResponse response = authServiceSpy.loginSuccess(user, request);

        reset(request);
        checkAssertions(response, defaultUrl);
    }

    public void checkAssertions(HttpResponse response, String expectedLocation) {

        // Check location is as expected
        String redirectLocation = response.getHeaders().get("Location");
        assertEquals(expectedLocation, redirectLocation);

        // Check cookies were not altered
        String cookieString = response.getHeaders().get("set-cookie");
        String[] splitCookie = cookieString.split("=");
        Map<String, String> responseCookies = new HashMap<>();
        for (Integer i = 0; i < splitCookie.length - 1; i++){
            if (i % 2 == 0){
                responseCookies.put(splitCookie[i], splitCookie[i+1]);
            }
        }

        // Check that the jwt cookie was returned
        String token = responseCookies.get("phylo-token");
        assertTrue(token.split("\\.").length == 3, "JWT was in wrong format");
    }

}
