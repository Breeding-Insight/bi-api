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
package org.breedinginsight.brapi.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
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
import javax.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class TokenController {

    private SecurityService securityService;
    private TokenService tokenService;

    @Property(name = "backend.base-url")
    protected String backendBaseUrl;

    @Property(name = "micronaut.bi.api.version")
    protected String biapiVersion;

    @Inject
    public TokenController(SecurityService securityService, TokenService tokenService) {
        this.securityService = securityService;
        this.tokenService = tokenService;
    }

    @Get("/api-token")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse apiToken(@QueryValue @NotBlank String returnUrl) {

        AuthenticatedUser actingUser = securityService.getUser();
        Optional<ApiToken> token = tokenService.generateApiToken(actingUser);

        // FieldBook sends user to brapi-authorize endpoint
        // brapi authorize endpoint sends user to bi-web authorization page
        // User logs in and is redirected to orcid
        // User logs into orcid
        // Orcid back to bi-api authorization flow
        // Bi-api returns user to that authorization page
        // User sees fieldbook authorize page on bi-web
        // User clicks authorize button
        // bi-web sends user to this endpoint

        if(token.isPresent()) {
            ApiToken apiToken = token.get();

            URI location = UriBuilder.of(returnUrl)
                    .queryParam("status", 200)
                    .queryParam("access_token", apiToken.getAccessToken())
                    .build();

            return HttpResponse.seeOther(location)
                    .header("Cache-Control","no-store")
                    .header("Pragma", "no-cache");
        } else {
            return HttpResponse.serverError();
        }
    }

    @Get("/programs/{programId}/.well-known/openid-configuration")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse getConfiguration(@PathVariable("programId") UUID programId) {

        /*
            {
              "issuer": "",
              "authorization_endpoint": "<URL TO LOGIN PAGE>",
              "jwks_uri": "",
              "token_endpoint": "",
              "grant_types_supported": ["implicit"],
              "response_types_supported": ["token"],
              "subject_types_supported": ["public"],
              "id_token_signing_alg_values_supported": []
            }
         */
        JsonObject configuration = new JsonObject();
        configuration.add("issuer", new JsonPrimitive(""));
        String authorizeUrl = String.format("%s/%s/programs/%s/brapi/authorize", backendBaseUrl, biapiVersion, programId);
        configuration.add("authorization_endpoint", new JsonPrimitive(authorizeUrl));
        configuration.add("jwks_uri", new JsonPrimitive(""));
        configuration.add("token_endpoint", new JsonPrimitive(""));
        JsonArray grants = new JsonArray();
        grants.add("implict");
        configuration.add("grant_types_supported", grants);
        JsonArray responseTypes = new JsonArray();
        responseTypes.add("token");
        configuration.add("response_types_supported", responseTypes);
        JsonArray subjectTypes = new JsonArray();
        subjectTypes.add("public");
        configuration.add("subject_types_supported", subjectTypes);
        JsonArray signingValues = new JsonArray();
        configuration.add("id_token_signing_alg_values_supported", signingValues);

        return HttpResponse.ok(configuration);
    }

}
