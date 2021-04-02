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
import org.brapi.client.v2.model.exceptions.HttpBadRequestException;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.brapps.importer.model.base.BrAPIPreviewResponse;
import org.breedinginsight.brapps.importer.model.imports.MappedImport;
import org.breedinginsight.brapps.importer.model.mapping.BrAPIImportMapping;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.BrAPIImportConfigManager;
import org.breedinginsight.brapps.importer.model.config.ImportConfig;
import org.breedinginsight.brapps.importer.services.BrAPIFileImportService;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.UnsupportedTypeException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class ImportController {

    private BrAPIImportConfigManager importManager;
    private SecurityService securityService;
    private BrAPIFileImportService brAPIFileImportService;

    @Inject
    ImportController(BrAPIImportConfigManager brAPIImportConfigManager, SecurityService securityService, BrAPIFileImportService brAPIFileImportService) {
        this.importManager = brAPIImportConfigManager;
        this.securityService = securityService;
        this.brAPIFileImportService = brAPIFileImportService;
    }

    @Get("/import/types")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    @AddMetadata
    public HttpResponse<Response<DataResponse<ImportConfig>>> getImportTypes() {
        List<ImportConfig> configs = brAPIFileImportService.getAllImportTypeConfigs();

        //TODO: Add actual page size
        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        Pagination pagination = new Pagination(configs.size(), 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<ImportConfig>> response = new Response(metadata, new DataResponse<>(configs));
        return HttpResponse.ok(response);
    }

    @Get("/programs/{programId}/import/mapping{?draft")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<DataResponse<BrAPIImportMapping>>> getMappings(@PathVariable UUID programId,
                                                                                @QueryValue(defaultValue = "false") Boolean draft) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            List<BrAPIImportMapping> result = brAPIFileImportService.getAllMappings(programId, actingUser, draft);
            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            Pagination pagination = new Pagination(result.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response<DataResponse<BrAPIImportMapping>> response = new Response(metadata, new DataResponse<>(result));
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        }

    }

    @Post("/programs/{programId}/import/mapping/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<BrAPIImportMapping>> createMapping(@PathVariable UUID programId, @Part CompletedFileUpload file) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            BrAPIImportMapping result = brAPIFileImportService.createMapping(programId, actingUser, file);
            Response<BrAPIImportMapping> response = new Response(result);
            //TODO: Not returned response for some reason
            return HttpResponse.ok(response);
        } catch (HttpBadRequestException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (UnsupportedTypeException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
        }
    }

    @Put("programs/{programId}/import/mapping/{mappingId}/file{?validate}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<BrAPIImportMapping>> editMappingFile(@PathVariable UUID programId, @PathVariable UUID mappingId,
                                                                  @Part("file") CompletedFileUpload file,
                                                                  @QueryValue(defaultValue="true") Boolean validate) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            BrAPIImportMapping result = brAPIFileImportService.updateMappingFile(programId, mappingId, actingUser, file);
            Response<BrAPIImportMapping> response = new Response(result);
            //TODO: Not returned response for some reason
            return HttpResponse.ok(response);
        } catch (HttpBadRequestException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (UnsupportedTypeException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
        }
    }

    @Put("programs/{programId}/import/mapping/{mappingId}{?validate}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<BrAPIImportMapping>> editMapping(@PathVariable UUID programId, @PathVariable UUID mappingId,
                                                                      @Body BrAPIImportMapping mapping,
                                                                      @QueryValue(defaultValue="true") Boolean validate) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            BrAPIImportMapping result = brAPIFileImportService.updateMapping(programId, actingUser, mappingId, mapping, validate);
            Response<BrAPIImportMapping> response = new Response(result);
            return HttpResponse.ok(response);
        } catch (HttpBadRequestException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.BAD_REQUEST, e.getMessage());
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

    @Post("programs/{programId}/import/mapping/{mappingId}/data{?commit}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<BrAPIPreviewResponse>> uploadData(@PathVariable UUID programId, @PathVariable UUID mappingId,
                                                                   @Part("file") CompletedFileUpload file,
                                                                   @QueryValue(defaultValue="false") Boolean commit) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            BrAPIPreviewResponse result = brAPIFileImportService.uploadData(programId, mappingId, actingUser, file, commit);
            Response<BrAPIPreviewResponse> response = new Response(result);
            return HttpResponse.ok(response);
        } catch (HttpBadRequestException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.BAD_REQUEST, e.getMessage());
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



}
