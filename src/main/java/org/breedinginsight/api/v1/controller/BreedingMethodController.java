package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

    private SecurityService securityService;
    private BreedingMethodDAO breedingMethodDAO;

    @Inject
    public BreedingMethodController(SecurityService securityService, BreedingMethodDAO breedingMethodDAO) {
        this.securityService = securityService;
        this.breedingMethodDAO = breedingMethodDAO;
    }

    @Get("/breeding-methods")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<ProgramBreedingMethodEntity>>> getSystemBreedingMethods() {
        log.debug("fetching system breeding methods");

        try {
            AuthenticatedUser actingUser = securityService.getUser();

            List<ProgramBreedingMethodEntity> breedingMethods = breedingMethodDAO.getSystemBreedingMethods();
            return buildResponse(breedingMethods);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerException("Error fetching system breeding methods", e);
        }
    }

    @Post("programs/{programId}/breeding-methods")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<ProgramBreedingMethodEntity>> createProgramBreedingMethod(@PathVariable UUID programId, @Body ProgramBreedingMethodEntity breedingMethod) throws BadRequestException {
        log.debug("Saving new program breeding method");

        try {
            AuthenticatedUser user = securityService.getUser();
            if(!validateBreedingMethod(breedingMethod)) {
                throw new BadRequestException("Missing required data");
            }
            Response<ProgramBreedingMethodEntity> response = new Response<>();
            response.result = breedingMethodDAO.createProgramMethod(breedingMethod, programId, user.getId());

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Creation"));
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            response.metadata = metadata;

            return HttpResponse.ok(response);
        } catch (Exception e) {
            log.error("Error creating program breeding method", e);
            throw e;
        }
    }

    @Get("programs/{programId}/breeding-methods")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<ProgramBreedingMethodEntity>>> getProgramBreedingMethods(@PathVariable UUID programId) {
        log.debug(String.format("fetching breeding methods for program: %s", programId));

        try {
            AuthenticatedUser actingUser = securityService.getUser();

            List<ProgramBreedingMethodEntity> breedingMethods = breedingMethodDAO.getProgramBreedingMethods(programId);
            return buildResponse(breedingMethods);
//        } catch (DoesNotExistException e) {
//            log.info(e.getMessage(), e);
//            return HttpResponse.notFound();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new InternalServerException("Error fetching breeding methods", e);
        }
    }

    @Put("programs/{programId}/breeding-methods/{breedingMethodId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<ProgramBreedingMethodEntity>> updateProgramBreedingMethod(@PathVariable UUID programId, @PathVariable UUID breedingMethodId, @Body ProgramBreedingMethodEntity breedingMethod) throws BadRequestException {
        log.debug("Saving new program breeding method");

        try {
            AuthenticatedUser user = securityService.getUser();

            if(!validateBreedingMethod(breedingMethod)) {
                throw new BadRequestException("Missing required data");
            }
            breedingMethod.setId(breedingMethodId);

            Response<ProgramBreedingMethodEntity> response = new Response<>();
            response.result = breedingMethodDAO.updateProgramMethod(breedingMethod, programId, user.getId());

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Update"));
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            response.metadata = metadata;

            return HttpResponse.ok(response);
        } catch (Exception e) {
            log.error("Error updating program breeding method", e);
            throw e;
        }
    }

    @Put("programs/{programId}/breeding-methods/enable")
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse enableSystemBreedingMethods(@PathVariable UUID programId, @Body List<UUID> systemBreedingMethodIds) {
        log.debug("enabling system breeding methods for program: "+programId);

        try {
            AuthenticatedUser user = securityService.getUser();

            breedingMethodDAO.enableSystemMethods(systemBreedingMethodIds, programId, user.getId());
            return HttpResponse.ok();
        } catch (Exception e) {
            log.error("Error enabling system breeding methods for program: "+programId, e);
            throw e;
        }
    }

    private boolean validateBreedingMethod(ProgramBreedingMethodEntity method) {
        return StringUtils.isNotBlank(method.getName())
                && StringUtils.isNotBlank(method.getAbbreviation())
                && StringUtils.isNotBlank(method.getCategory())
                && StringUtils.isNotBlank(method.getGeneticDiversity());
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
