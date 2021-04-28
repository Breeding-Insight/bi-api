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
package org.breedinginsight.brapi.v1.controller;

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
import org.breedinginsight.daos.ProgramDAO;

import javax.inject.Inject;
import javax.validation.constraints.NotBlank;
import java.net.URI;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class BrapiAuthorizeController {

    @Inject
    private ProgramDAO programDAO;

    @Property(name = "web.base-url")
    protected String webBaseUrl;

    @Get("/programs/{programId}/brapi/authorize")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse authorize(@QueryValue @NotBlank String display_name, @QueryValue @NotBlank String return_url, @PathVariable("programId") String programId) {
        if(programDAO.existsById(UUID.fromString(programId))) {
            URI location = UriBuilder.of(String.format("%s/programs/%s/brapi/authorize", webBaseUrl, programId))
                                     .queryParam("display_name", display_name)
                                     .queryParam("return_url", return_url)
                                     .build();
            return HttpResponse.seeOther(location);
        }

        return HttpResponse.notFound();
    }

}
