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
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRole;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.services.ImportConfigManager;
import org.breedinginsight.brapps.importer.model.config.ImportConfigResponse;
import org.breedinginsight.brapps.importer.services.FileImportService;
import org.breedinginsight.services.exceptions.*;
import org.jooq.tools.StringUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class MappingController {

    private ImportConfigManager importManager;
    private SecurityService securityService;
    private FileImportService fileImportService;

    @Inject
    MappingController(ImportConfigManager brAPIImportConfigManager, SecurityService securityService, FileImportService fileImportService) {
        this.importManager = brAPIImportConfigManager;
        this.securityService = securityService;
        this.fileImportService = fileImportService;
    }

    @Get("/import/templates")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    @AddMetadata
    public HttpResponse<Response<DataResponse<ImportConfigResponse>>> getImportTemplates() {
        List<ImportConfigResponse> configs = fileImportService.getAllImportTypeConfigs();

        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        Pagination pagination = new Pagination(configs.size(), 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<ImportConfigResponse>> response = new Response(metadata, new DataResponse<>(configs));
        return HttpResponse.ok(response);
    }

    // TODO: Probably don't need
    @Get("/import/mappings{?importName}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<DataResponse<ImportMapping>>> getSystemMappings(@Nullable @QueryValue String importName) {

        AuthenticatedUser actingUser = securityService.getUser();
        List<ImportMapping> result;
        if (StringUtils.isBlank(importName)){
            result = fileImportService.getAllSystemMappings(actingUser);
        } else {
            result = fileImportService.getSystemMappingByName(actingUser, importName);
        }
        log.info("...............");
        result.forEach((r) -> System.out.println(r.getId() + " ||| "));
        log.info("...............");

        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        Pagination pagination = new Pagination(result.size(), result.size(), 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<ImportMapping>> response = new Response(metadata, new DataResponse<>(result));
        return HttpResponse.ok(response);
    }

    /**
     * Gets all saved mappings from a given program. Does not return the mapping configurations.
     * Use the mapping details endpoint for that.
     * @param programId
     * @return
     */
    @Get("/programs/{programId}/import/mappings")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER, ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<DataResponse<ImportMapping>>> getMappings(@PathVariable UUID programId) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            List<ImportMapping> result = fileImportService.getAllMappings(programId, actingUser);
            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            Pagination pagination = new Pagination(result.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response<DataResponse<ImportMapping>> response = new Response(metadata, new DataResponse<>(result));
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    /**
     * Gets a single mapping with the mapping configuration in the response.
     * @param programId
     * @param mappingId
     * @return
     */
    @Get("/programs/{programId}/import/mappings/{mappingId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER, ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<ImportMapping>> getMappingDetails(@PathVariable UUID programId, @PathVariable UUID mappingId) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ImportMapping result = fileImportService.getMappingDetails(mappingId);
            return HttpResponse.ok(new Response(result));
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    /**
     * Saves a new mapping for an import template. Requires valid mapping format, but does not enforce matching to
     * template columns.
     * @param programId
     * @param mapping
     * @return
     */
    @Post("/programs/{programId}/import/mappings")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<ImportMapping>> createMapping(@PathVariable UUID programId, @Body ImportMapping mapping) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ImportMapping result = fileImportService.createMapping(programId, actingUser, mapping);
            Response<ImportMapping> response = new Response(result);
            return HttpResponse.ok(response);
        } catch (AlreadyExistsException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch (UnprocessableEntityException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    /**
     * Updates existing mapping. Requires valid mapping format, but does not enforce matching to
     * template columns.
     * @param programId
     * @param mappingId
     * @param mapping
     * @return
     */
    @Put("programs/{programId}/import/mappings/{mappingId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<ImportMapping>> updateMapping(@PathVariable UUID programId, @PathVariable UUID mappingId,
                                                             @Body ImportMapping mapping) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ImportMapping result = fileImportService.updateMapping(programId, actingUser, mappingId, mapping);
            Response<ImportMapping> response = new Response(result);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (AlreadyExistsException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
