package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;

import javax.inject.Inject;
import javax.ws.rs.QueryParam;
import java.util.UUID;

public class OntologyController {

    private SecurityService securityService;
    private OntologyService ontologyService;

    @Inject
    private OntologyController(SecurityService securityService, OntologyService ontologyService) {
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
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<Trait>>> getAvailablePrograms(
            @PathVariable UUID programId, @QueryValue(defaultValue = "false") Boolean shared) {
        return HttpResponse.ok();
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
    public HttpResponse<Response<DataResponse<Trait>>> shareOntology(
            @PathVariable UUID programId) {
        return HttpResponse.ok();
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
