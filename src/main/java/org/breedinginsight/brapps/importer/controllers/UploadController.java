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

package org.breedinginsight.brapps.importer.controllers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRole;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.services.UploadService;
import org.breedinginsight.services.exceptions.*;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class UploadController {
    private SecurityService securityService;
    private UploadService uploadService;

    @Inject
    UploadController(SecurityService securityService, UploadService uploadService) {
        this.securityService = securityService;
        this.uploadService = uploadService;
    }

    /**
     * Imports a file for the specified import template. If a mapping id is specified, the mapping
     * will be used to map the file data to the template.
     * @param programId
     * @param templateId
     * @param file
     * @return
     */
    @Post("programs/{programId}/import/{templateId}{?mappingId}{?commit}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER, ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<ImportResponse>> upload(@PathVariable UUID programId, @PathVariable Integer templateId,
                                                         @QueryValue @Nullable UUID mappingId, @QueryValue(defaultValue = "false") Boolean commit,
                                                         @Part("file") CompletedFileUpload file, @Part("userInput") @Nullable Map<String, Object> userInput) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ImportResponse result = uploadService.uploadData(programId, templateId, mappingId, userInput, actingUser, file, commit);
            Response<ImportResponse> response = new Response(result);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.notFound();
        } catch (UnsupportedTypeException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
        } catch (UnprocessableEntityException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (ValidatorException e) {
            log.error("Validation errors", e);
            HttpResponse response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.getErrors());
            return response;
        }
    }


    @Get("programs/{programId}/import/{uploadId}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER, ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<ImportResponse>> getUpload(@PathVariable UUID programId, @PathVariable UUID uploadId) {
        try {
            Pair<HttpStatus, ImportResponse> result = uploadService.getDataUpload(uploadId);
            Response<ImportResponse> response = new Response(result.getRight());
            if (result.getLeft().equals(HttpStatus.ACCEPTED)) {
                return HttpResponse.ok(response).status(result.getLeft());
            } else {
                return HttpResponse.ok(response);
            }
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }
}
