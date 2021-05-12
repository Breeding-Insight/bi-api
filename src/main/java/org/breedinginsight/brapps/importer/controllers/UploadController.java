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
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.services.FileImportService;
import org.breedinginsight.brapps.importer.services.ImportConfigManager;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.UnsupportedTypeException;

import javax.inject.Inject;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class UploadController {
    private SecurityService securityService;
    private FileImportService fileImportService;

    @Inject
    UploadController(SecurityService securityService, FileImportService fileImportService) {
        this.securityService = securityService;
        this.fileImportService = fileImportService;
    }

    @Post("programs/{programId}/import/mappings/{mappingId}/data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<ImportResponse>> uploadData(@PathVariable UUID programId, @PathVariable UUID mappingId,
                                                             @Part("file") CompletedFileUpload file) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            // TODO: We know this will be a 202
            ImportResponse result = fileImportService.uploadData(programId, mappingId, actingUser, file);
            Response<ImportResponse> response = new Response(result);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (UnsupportedTypeException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
        } catch (UnprocessableEntityException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }


    @Get("programs/{programId}/import/mappings/{mappingId}/data/{uploadId}{?mapping}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<ImportResponse>> uploadData(@PathVariable UUID programId, @PathVariable UUID mappingId,
                                                             @PathVariable UUID uploadId, @QueryValue(defaultValue = "false") Boolean mapping) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            Pair<HttpStatus, ImportResponse> result = fileImportService.getDataUpload(uploadId, mapping);
            Response<ImportResponse> response = new Response(result.getRight());
            return HttpResponse.ok(response).status(result.getLeft());
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    @Put("programs/{programId}/import/mappings/{mappingId}/data/{uploadId}/commit")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<ImportResponse>> commitData(@PathVariable UUID programId, @PathVariable UUID mappingId,
                                                             @PathVariable UUID uploadId) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            // TODO: We know this will be a 202
            ImportResponse result = fileImportService.updateUpload(programId, uploadId, actingUser, true);
            Response<ImportResponse> response = new Response(result);
            return HttpResponse.ok(response).status(HttpStatus.ACCEPTED);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (UnprocessableEntityException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @Put("programs/{programId}/import/mappings/{mappingId}/data/{uploadId}/preview")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<ImportResponse>> previewData(@PathVariable UUID programId, @PathVariable UUID mappingId,
                                                              @PathVariable UUID uploadId) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            // TODO: We know this will be a 202
            ImportResponse result = fileImportService.updateUpload(programId, uploadId, actingUser, false);
            Response<ImportResponse> response = new Response(result);
            return HttpResponse.ok(response).status(HttpStatus.ACCEPTED);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (UnprocessableEntityException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @Get("programs/{programId}/import/mappings/{mappingId}/data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<ImportResponse>> getActiveUploads(@PathVariable UUID programId, @PathVariable UUID mappingId,
                                                                   @PathVariable UUID uploadId) {
        // Gets the active uploads that are committing data
        // We want to see, mapping uploads, and committed uploads
        // Don't want a duplicate for each preview (one mapping, one committed)
        // POST /data -> Creates the upload, saves the data
        // GET /data/{importId} -> checks the status of the current import
        // PUT /data/{importId}/commit -> commits the data
        // PUT /data/{importId}/preview -> gets a fresh preview of the data
        // GET /data -> Gets all imports
        // Will need to check for ongoing imports for that id to make sure its not in processing currently

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            Pair<HttpStatus, ImportResponse> result = fileImportService.getDataUpload(uploadId, false);
            Response<ImportResponse> response = new Response(result.getRight());
            return HttpResponse.ok(response).status(result.getLeft());
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }
}
