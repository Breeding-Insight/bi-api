package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.request.SharedOntologyProgramRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.SharedProgram;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class OntologyController {

    private SecurityService securityService;
    private OntologyService ontologyService;

    @Inject
    public OntologyController(SecurityService securityService, OntologyService ontologyService) {
        this.securityService = securityService;
        this.ontologyService = ontologyService;
    }

    /**
     * Returns all available programs available to share programs ontology with. Includes programs
     * currently being shared with and their editable (unshareable) status.
     *
     * @param programId
     * @param shared
     * @return
     * {
     *     data: [
     *      {
     *          name: string, -- Program name
     *          id: UUID, -- Program ID
     *          shared: true,
     *          status: ACCEPTED | PENDING
     *      }
     *     ]
     * }
     */
    @Get("/programs/{programId}/ontology/shared/programs{?shared}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<SharedProgram>>> getAvailablePrograms(
            @PathVariable UUID programId, @QueryValue(defaultValue = "false") Boolean shared) {
        List<SharedProgram> sharedPrograms = ontologyService.getSharedOntology(programId, shared);
        List<org.breedinginsight.api.model.v1.response.metadata.Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Creation"));
        Pagination pagination = new Pagination(sharedPrograms.size(), 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);
        Response<DataResponse<SharedProgram>> response = new Response(metadata, new DataResponse<>(sharedPrograms));
        return HttpResponse.ok(response);
    }

    /**
     * Accepts a list of programs to shared the ontology with.
     * @param programId
     * @return List of programs successfully shared to with acceptable status
     * {
     *     data: [
     *      {
     *          name: string, -- Program name
     *          id: UUID, -- Program ID
     *          shared: true,
     *          status: ACCEPTED | PENDING
     *      }
     *     ]
     * }
     */
    @Post("/programs/{programId}/ontology/shared/programs")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<SharedProgram>>> shareOntology(
            @PathVariable UUID programId, @Body List<SharedOntologyProgramRequest> request) {
        try {
            List<SharedProgram> sharedPrograms = ontologyService.shareOntology(programId, securityService.getUser(), request);
            List<org.breedinginsight.api.model.v1.response.metadata.Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Creation"));
            Pagination pagination = new Pagination(sharedPrograms.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<SharedProgram>> response = new Response(metadata, new DataResponse<>(sharedPrograms));
            return HttpResponse.ok(response);
        } catch (ValidatorException e) {
            log.error("Validation errors", e);
            HttpResponse response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.getErrors());
            return response;
        } catch (UnprocessableEntityException e) {
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    /**
     * Revokes access to shared ontology from a program. If program is not currently shared with
     * will still return a 200.
     *
     * @param programId
     * @param sharedProgramId
     * @return
     */
    @Delete("/programs/{programId}/ontology/shared/programs/{sharedProgramId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<Trait>>> revokeOntology(
            @PathVariable UUID programId, @PathVariable UUID sharedProgramId) {
        return HttpResponse.ok();
    }
}
