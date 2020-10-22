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
package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.model.ApiToken;
import org.breedinginsight.services.TokenService;

import javax.inject.Inject;
import java.net.URI;
import java.util.Optional;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class TokenController {

    private SecurityService securityService;
    private TokenService tokenService;

    @Inject
    public TokenController(SecurityService securityService, TokenService tokenService) {
        this.securityService = securityService;
        this.tokenService = tokenService;
    }

    @Get("/api-token")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse apiToken(@QueryValue String returnUrl) {

        AuthenticatedUser actingUser = securityService.getUser();
        Optional<ApiToken> token = tokenService.generateApiToken(actingUser);

        if(token.isPresent()) {
            ApiToken apiToken = token.get();

            URI location = UriBuilder.of(returnUrl)
                    .queryParam("status", 200)
                    .queryParam("token", apiToken.getAccessToken())
                    .build();

            return HttpResponse.seeOther(location)
                    .header("Cache-Control","no-store")
                    .header("Pragma", "no-cache");
        } else {
            return HttpResponse.serverError();
        }

    }

}
