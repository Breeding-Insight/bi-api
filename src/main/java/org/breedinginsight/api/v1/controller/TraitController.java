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
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.request.query.TraitsQuery;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.api.model.v1.validators.SearchValid;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.Editable;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.exceptions.BadRequestException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.TraitQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class TraitController {

    private TraitService traitService;
    private SecurityService securityService;
    private TraitQueryMapper traitQueryMapper;
    private OntologyService ontologyService;

    @Inject
    public TraitController(TraitService traitService, SecurityService securityService,
                           TraitQueryMapper traitQueryMapper, OntologyService ontologyService){
        this.traitService = traitService;
        this.securityService = securityService;
        this.traitQueryMapper = traitQueryMapper;
        this.ontologyService = ontologyService;
    }

    @Get("/programs/{programId}/traits{?traitsQuery*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<Trait>>> getTraits(
            @PathVariable UUID programId,
            @QueryValue @QueryValid(using = TraitQueryMapper.class) @Valid TraitsQuery traitsQuery) {

        try {
            List<Trait> traits = ontologyService.getTraitsByProgramId(programId, traitsQuery.getFull());
            return ResponseUtils.getQueryResponse(traits, traitQueryMapper, traitsQuery);
        } catch (DoesNotExistException e) {
            return HttpResponse.notFound();
        }
    }

    @Post("/programs/{programId}/traits/search{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<Program>>> postTraitsSearch(
            @PathVariable UUID programId,
            @QueryValue @QueryValid(using = TraitQueryMapper.class) @Valid QueryParams queryParams,
            @Body @SearchValid(using = TraitQueryMapper.class) SearchRequest searchRequest) {

        try {
            List<Trait> traits = ontologyService.getTraitsByProgramId(programId, true);
            return ResponseUtils.getQueryResponse(traits, traitQueryMapper, searchRequest, queryParams);
        } catch (DoesNotExistException e) {
            return HttpResponse.notFound();
        }
    }

    @Get("/programs/{programId}/traits/{traitId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<Trait>> getTrait(@PathVariable UUID programId, @PathVariable UUID traitId) {

        try {
            Optional<Trait> trait = traitService.getById(programId, traitId);
            if (trait.isPresent()) {
                Response<Trait> response = new Response<>(trait.get());
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.notFound();
            }
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }

    }

    @Get("/programs/{programId}/traits/{traitId}/editable")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<Editable>> getTraitEditable(@PathVariable UUID programId, @PathVariable UUID traitId) {

        Editable editable = traitService.getEditable(programId, traitId);
        Response<Editable> response = new Response<>(editable);
        return HttpResponse.ok(response);

    }

    @Post("/programs/{programId}/traits")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<DataResponse<Trait>>> createTraits(@PathVariable UUID programId, @Body @Valid List<Trait> traits) {
        AuthenticatedUser actingUser = securityService.getUser();
        try {
            List<Trait> createdTraits = ontologyService.createTraits(programId, traits, actingUser, true);
            List<Status> metadataStatus = new ArrayList<>();
            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Creation"));
            // Construct our metadata and response
            Pagination pagination = new Pagination(createdTraits.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<Trait>> response = new Response<>(metadata, new DataResponse<>(createdTraits));
            return HttpResponse.ok(response);
        } catch (BadRequestException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (ValidatorException e){
            log.info(e.getErrors().toString());
            HttpResponse response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.getErrors());
            return response;
        }
    }

    @Put("/programs/{programId}/traits")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<DataResponse<Trait>>> updateTraits(@PathVariable UUID programId, @Body @Valid List<Trait> traits) {
        AuthenticatedUser actingUser = securityService.getUser();
        try {
            List<Trait> updatedTraits = ontologyService.updateTraits(programId, traits, actingUser);
            List<Status> metadataStatus = new ArrayList<>();
            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Creation"));
            // Construct our metadata and response
            Pagination pagination = new Pagination(updatedTraits.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<Trait>> response = new Response<>(metadata, new DataResponse<>(updatedTraits));
            return HttpResponse.ok(response);
        } catch (BadRequestException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (ValidatorException e){
            log.info(e.getErrors().toString());
            HttpResponse response = HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(e.getErrors());
            return response;
        }
    }

    @Put("/programs/{programId}/traits/{traitId}/archive{?active}")
    @AddMetadata
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<Trait>> archiveTrait(@PathVariable UUID programId, @PathVariable UUID traitId, @QueryValue(defaultValue = "false") Boolean active) {

        AuthenticatedUser actingUser = securityService.getUser();
        try {
            Trait updatedTrait = traitService.setTraitActiveStatus(programId, traitId, active, actingUser);
            Response<Trait> response = new Response<>(updatedTrait);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    @Get("/programs/{programId}/traits/tags")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<String>>> getAllTraitTags(
            @PathVariable UUID programId) {

        List<String> tags = traitService.getAllTraitTags(programId);
        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Creation"));
        Pagination pagination = new Pagination(tags.size(), 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);
        Response<DataResponse<String>> response = new Response<>(metadata, new DataResponse<>(tags));
        return HttpResponse.ok(response);
    }
}
