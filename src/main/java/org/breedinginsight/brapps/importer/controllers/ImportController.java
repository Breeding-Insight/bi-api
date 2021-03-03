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
import org.breedinginsight.brapps.importer.model.BrAPIImportMapping;
import org.breedinginsight.model.ProgramUpload;
import org.breedinginsight.brapps.importer.model.BrAPIImportConfigManager;
import org.breedinginsight.brapps.importer.model.response.ImportConfig;
import org.breedinginsight.brapps.importer.services.BrAPIImportService;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
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
    private BrAPIImportService brAPIImportService;

    @Inject
    ImportController(BrAPIImportConfigManager brAPIImportConfigManager, SecurityService securityService, BrAPIImportService brAPIImportService) {
        this.importManager = brAPIImportConfigManager;
        this.securityService = securityService;
        this.brAPIImportService = brAPIImportService;
    }

    @Get("/import/types")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    @AddMetadata
    public HttpResponse<Response<DataResponse<ImportConfig>>> getImportTypes() {
        List<ImportConfig> configs = importManager.getAllTypeConfigs();

        //TODO: Add actual page size
        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        Pagination pagination = new Pagination(1, 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<ImportConfig>> response = new Response(metadata, new DataResponse<>(configs));
        return HttpResponse.ok(response);
    }

    @Post("/programs/{programId}/import/mapping")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<BrAPIImportMapping>> createMapping(@PathVariable UUID programId, @Part CompletedFileUpload file) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            BrAPIImportMapping result = brAPIImportService.saveMapping(programId, actingUser, file);
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

    @Put("program/{programId}/import/mapping/{mappingId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    @AddMetadata
    public HttpResponse<Response<DataResponse<ImportConfig>>> editMapping(@PathVariable UUID programId, @PathVariable UUID mappingId) {
        List<ImportConfig> configs = importManager.getAllTypeConfigs();

        //TODO: Add actual page size
        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        Pagination pagination = new Pagination(1, 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<ImportConfig>> response = new Response(metadata, new DataResponse<>(configs));
        return HttpResponse.ok(response);
    }

}
