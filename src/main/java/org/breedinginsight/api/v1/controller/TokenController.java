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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.ApiToken;
import org.breedinginsight.services.TokenService;

import javax.inject.Inject;
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
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse apiToken() {

        AuthenticatedUser actingUser = securityService.getUser();
        Optional<ApiToken> token = tokenService.generateApiToken(actingUser);

        if(token.isPresent()) {
            Response<ApiToken> response = new Response(token.get());
            return HttpResponse.ok(response)
                    .header("Cache-Control","no-store")
                    .header("Pragma", "no-cache");
        } else {
            return HttpResponse.serverError();
        }

    }

}
