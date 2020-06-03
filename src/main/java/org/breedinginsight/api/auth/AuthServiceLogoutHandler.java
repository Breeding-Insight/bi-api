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