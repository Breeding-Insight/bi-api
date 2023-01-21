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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRole;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.request.SharedOntologyProgramRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.SharedOntology;
import org.breedinginsight.model.SubscribedOntology;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
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
     *          programName: string, -- Program name
     *          programId: UUID, -- Program ID
     *          shared: boolean,
     *          accepted: boolean || null, -- null if shared = false
     *          editable: boolean || null   -- null if shared = false
     *      }
     *     ]
     * }
     */
    @Get("/programs/{programId}/ontology/shared/programs{?shared}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<DataResponse<SharedOntology>>> getAvailablePrograms(
            @PathVariable UUID programId, @QueryValue(defaultValue = "false") Boolean shared) {
        try {
            List<SharedOntology> sharedOntologies = ontologyService.getSharedOntology(programId, shared);
            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            Pagination pagination = new Pagination(sharedOntologies.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<SharedOntology>> response = new Response(metadata, new DataResponse<>(sharedOntologies));
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Accepts a list of programs to shared the ontology with.
     * @param programId
     * @return List of programs successfully shared to with acceptable status
     * {
     *     data: [
     *      {
     *          programName: string, -- Program name
     *          programId: UUID, -- Required. Program ID
     *      }
     *     ]
     * }
     */
    @Post("/programs/{programId}/ontology/shared/programs")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<DataResponse<SharedOntology>>> shareOntology(
            @PathVariable UUID programId, @Body List<SharedOntologyProgramRequest> request) {
        try {
            List<SharedOntology> sharedOntologies = ontologyService.shareOntology(programId, securityService.getUser(), request);
            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Creation"));
            Pagination pagination = new Pagination(sharedOntologies.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<SharedOntology>> response = new Response(metadata, new DataResponse<>(sharedOntologies));
            return HttpResponse.ok(response);
        } catch (ValidatorException e) {
            log.error("Validation errors", e);
            HttpResponse response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.getErrors());
            return response;
        } catch (UnprocessableEntityException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Revokes access to shared ontology from a program. If program is not currently shared with
     * will return 404.
     *
     * @param programId
     * @param sharedProgramId
     * @return
     */
    @Delete("/programs/{programId}/ontology/shared/programs/{sharedProgramId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse revokeOntology(
            @PathVariable UUID programId, @PathVariable UUID sharedProgramId) {
        try {
            ontologyService.revokeOntology(programId, sharedProgramId);
            return HttpResponse.ok();
        } catch (UnprocessableEntityException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Scoped under the program that is subscribing. Accept a shared ontology.
     *
     * @param programId -- Program that is subscribing to an ontology
     * @param sharingProgramId -- Program that has shared its ontology.
     * @return
     */
    @Put("/programs/{programId}/ontology/subscribe/{sharingProgramId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<SubscribedOntology>> subscribeOntology(
            @PathVariable UUID programId, @PathVariable UUID sharingProgramId) {
        try {
            SubscribedOntology shareRequest = ontologyService.subscribeOntology(programId, sharingProgramId);
            Response<SubscribedOntology> response = new Response(shareRequest);
            return HttpResponse.ok(response);
        } catch (UnprocessableEntityException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Scoped under the program that is subscribing. Unsubscribe from a shared ontology.
     *
     * @param programId -- Program that is unsubscribing from an ontology
     * @param sharingProgramId -- Program that has shared its ontology.
     * @return
     */
    @Delete("/programs/{programId}/ontology/subscribe/{sharingProgramId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse unsubscribeOntology(
            @PathVariable UUID programId, @PathVariable UUID sharingProgramId) {
        try {
            ontologyService.unsubscribeOntology(programId, sharingProgramId);
            return HttpResponse.ok();
        } catch (UnprocessableEntityException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Retrieves subscription options for programs that have shared their ontology with the requesting program.
     * Will indicate whether the program has subscribed to a given ontology or not.
     *
     * @param programId -- Program request information
     * @return
     * {
     *     programId,   -- Program that owns the ontology the request program may subscribe to.
     *     programName,
     *     subscribed,  -- boolean. Whether the requesting program is subscribed to this ontology or not.
     *     editable     -- boolean || null. Indicates whether this program can unsubscribe from this ontology or not. Null if not subscribed to this ontology.
     * }
     */
    @Get("/programs/{programId}/ontology/subscribe")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<DataResponse<SubscribedOntology>>> getSubscribedOntology(
            @PathVariable UUID programId) {
        try {
            List<SubscribedOntology> shareRequests = ontologyService.getSubscribeOntologyOptions(programId);
            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Creation"));
            Pagination pagination = new Pagination(shareRequests.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<SubscribedOntology>> response = new Response(metadata, new DataResponse<>(shareRequests));
            return HttpResponse.ok(response);
        }  catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
