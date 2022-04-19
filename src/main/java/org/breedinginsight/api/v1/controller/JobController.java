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
import io.micronaut.http.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRole;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.services.FileImportService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class JobController {
    private SecurityService securityService;
    private FileImportService fileImportService;

    @Inject
    public JobController(SecurityService securityService, FileImportService fileImportService) {
        this.securityService = securityService;
        this.fileImportService = fileImportService;
    }

    @Get("programs/{programId}/jobs")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER, ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<ImportResponse>> getProgramUploads(@PathVariable UUID programId, @QueryValue(defaultValue = "false") Boolean mapping) {
        log.debug(String.format("fetching processes for program: %s", programId));
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            List<ImportResponse> result = fileImportService.getProgramUploads(programId, mapping);
            Response<ImportResponse> response = new Response(result);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.notFound();
        }
    }
}
