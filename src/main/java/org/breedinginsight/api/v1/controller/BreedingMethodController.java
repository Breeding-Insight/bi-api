package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.breedinginsight.services.BreedingMethodService;
import org.breedinginsight.services.exceptions.BadRequestException;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class BreedingMethodController {

    private final SecurityService securityService;
    private final BreedingMethodService breedingMethodService;

    @Inject
    public BreedingMethodController(SecurityService securityService, BreedingMethodService breedingMethodService) {
        this.securityService = securityService;
        this.breedingMethodService = breedingMethodService;
    }

    @Get("/breeding-methods")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<ProgramBreedingMethodEntity>>> getSystemBreedingMethods() {
        log.debug("fetching system breeding methods");

        try {
            AuthenticatedUser actingUser = securityService.getUser();

            List<ProgramBreedingMethodEntity> breedingMethods = breedingMethodService.getSystemBreedingMethods();
            return buildResponse(breedingMethods);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerException("Error fetching system breeding methods", e);
        }
    }

    @Post("programs/{programId}/breeding-methods")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse createProgramBreedingMethod(@PathVariable UUID programId, @Body ProgramBreedingMethodEntity breedingMethod) throws BadRequestException, ApiException {
        log.debug("Saving new program breeding method");

        try {
            AuthenticatedUser user = securityService.getUser();
            Response<ProgramBreedingMethodEntity> response = new Response<>();
            response.result = breedingMethodService.createBreedingMethod(breedingMethod, programId, user.getId());

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Creation"));
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            response.metadata = metadata;

            return HttpResponse.ok(response);
        } catch (BadRequestException ex) {
            return HttpResponse.badRequest(ex.getMessage());
        } catch (Exception e) {
            log.error("Error creating program breeding method", e);
            throw e;
        }
    }

    @Get("programs/{programId}/breeding-methods{?inUse}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<ProgramBreedingMethodEntity>>> getProgramBreedingMethods(@PathVariable UUID programId, @QueryValue(defaultValue = "false") Boolean inUse) {
        log.debug(String.format("fetching breeding methods for program: %s", programId));

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            List<ProgramBreedingMethodEntity> breedingMethods;
            if(inUse) {
                breedingMethods = breedingMethodService.fetchBreedingMethodsInUse(programId);
            } else {
                 breedingMethods = breedingMethodService.getBreedingMethods(programId);
            }
            return buildResponse(breedingMethods);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerException("Error fetching breeding methods", e);
        }
    }

    @Put("programs/{programId}/breeding-methods/{breedingMethodId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse updateProgramBreedingMethod(@PathVariable UUID programId, @PathVariable UUID breedingMethodId, @Body ProgramBreedingMethodEntity breedingMethod) throws BadRequestException, ApiException {
        log.debug("Saving new program breeding method");

        try {
            AuthenticatedUser user = securityService.getUser();

            breedingMethod.setId(breedingMethodId);

            Response<ProgramBreedingMethodEntity> response = new Response<>();
            response.result = breedingMethodService.updateBreedingMethod(breedingMethod, programId, user.getId());

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Update"));
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            response.metadata = metadata;

            return HttpResponse.ok(response);
        } catch (BadRequestException ex) {
            return HttpResponse.badRequest(ex.getMessage());
        } catch (Exception e) {
            log.error("Error updating program breeding method", e);
            throw e;
        }
    }

    @Put("programs/{programId}/breeding-methods/enable")
// BI-1779 - Removing the ability to choose predefined methods for a program until we make the germplasm import template dynamically generated
//    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    @Secured(SecurityRule.DENY_ALL)
    public HttpResponse enableSystemBreedingMethods(@PathVariable UUID programId, @Body List<UUID> systemBreedingMethodIds) throws ApiException, BadRequestException {
        log.debug("enabling system breeding methods for program: "+programId);

        try {
            AuthenticatedUser user = securityService.getUser();

            breedingMethodService.enableSystemMethods(systemBreedingMethodIds, programId, user.getId());
            return HttpResponse.ok();
        } catch (Exception e) {
            log.error("Error enabling system breeding methods for program: "+programId, e);
            throw e;
        }
    }

    @Delete("programs/{programId}/breeding-methods/{breedingMethodId}")
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse deleteProgramBreedingMethod(@PathVariable UUID programId, @PathVariable UUID breedingMethodId) throws BadRequestException, ApiException {
        try {
            AuthenticatedUser user = securityService.getUser();
            breedingMethodService.deleteBreedingMethod(programId, breedingMethodId);
            return HttpResponse.ok();
        } catch (Exception e) {
            log.error("Error deleting breeding method.\n\tprogramId: " + programId + "\n\tbreedingMethodId: " + breedingMethodId);
            throw e;
        }
    }

    private HttpResponse<Response<DataResponse<ProgramBreedingMethodEntity>>> buildResponse(List<ProgramBreedingMethodEntity> breedingMethods) {
        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        Pagination pagination = new Pagination(breedingMethods.size(), breedingMethods.size(), 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);
        Response<DataResponse<ProgramBreedingMethodEntity>> response = new Response<>(metadata, new DataResponse<>(breedingMethods));
        return HttpResponse.ok(response);
    }
}
