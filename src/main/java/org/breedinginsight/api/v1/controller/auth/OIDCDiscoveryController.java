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

package org.breedinginsight.api.v1.controller.auth;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.daos.ProgramDAO;

import javax.inject.Inject;
import java.util.UUID;

@Controller
@Secured(SecurityRule.IS_ANONYMOUS)
public class OIDCDiscoveryController {

    private static final String OIDC_CONFIG = """
        {
          "issuer": "%s",
          "authorization_endpoint": "%s/programs/%s/brapi/authorize",
          "jwks_uri": "",
          "token_endpoint": "",
          "grant_types_supported": ["implicit"],
          "response_types_supported": ["token"],
          "subject_types_supported": ["public"],
          "id_token_signing_alg_values_supported": []
        }
    """;

    private final String webUrl;
    private final ProgramDAO programDAO;

    @Inject
    public OIDCDiscoveryController(@Property(name="web.base-url") String webUrl, ProgramDAO programDAO) {
        this.webUrl = webUrl;
        this.programDAO = programDAO;
    }


    @Get("/${micronaut.bi.api.version}/programs/{programId}/.well-known/openid-configuration")
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse getOpenIdConfig(UUID programId) {
        ProgramEntity programEntity = programDAO.fetchOneById(programId);
        if(programEntity != null) {
            return HttpResponse.ok(String.format(OIDC_CONFIG, webUrl, webUrl, programId));
        }

        return HttpResponse.notFound("unknown program");
    }
}
