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
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.request.*;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.api.model.v1.validators.SearchValid;
import org.breedinginsight.model.*;
import org.breedinginsight.services.ProgramObservationLevelService;
import org.breedinginsight.services.exceptions.*;
import org.breedinginsight.utilities.Utilities;
import org.breedinginsight.utilities.response.mappers.ProgramLocationQueryMapper;
import org.breedinginsight.utilities.response.mappers.ProgramQueryMapper;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.services.ProgramLocationService;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.ProgramUserQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ProgramController {

    private ProgramService programService;
    private ProgramUserService programUserService;
    private ProgramLocationService programLocationService;
    private SecurityService securityService;
    private ProgramQueryMapper programQueryMapper;
    private ProgramLocationQueryMapper programLocationQueryMapper;
    private ProgramUserQueryMapper programUserQueryMapper;
    private ProgramObservationLevelService programObservationLevelService;

    @Inject
    public ProgramController(ProgramService programService, ProgramUserService programUserService,
                             ProgramLocationService programLocationService, SecurityService securityService,
                             ProgramQueryMapper programQueryMapper, ProgramLocationQueryMapper programLocationQueryMapper,
                             ProgramUserQueryMapper programUserQueryMapper, ProgramObservationLevelService programObservationLevelService) {
        this.programService = programService;
        this.programUserService = programUserService;
        this.programLocationService = programLocationService;
        this.securityService = securityService;
        this.programQueryMapper = programQueryMapper;
        this.programLocationQueryMapper = programLocationQueryMapper;
        this.programUserQueryMapper = programUserQueryMapper;
        this.programObservationLevelService = programObservationLevelService;
    }

    @Get("/programs{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<Response<DataResponse<Program>>> getPrograms(
            @QueryValue @QueryValid(using = ProgramQueryMapper.class) @Valid QueryParams queryParams) {

        List<Program> programs = programService.getAll(securityService.getUser());
        return ResponseUtils.getQueryResponse(programs, programQueryMapper, queryParams);
    }

    @Post("/programs/search{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse<Response<DataResponse<Program>>> postProgramsSearch(
            @QueryValue @QueryValid(using = ProgramQueryMapper.class) @Valid QueryParams queryParams,
            @Body @SearchValid(using = ProgramQueryMapper.class) SearchRequest searchRequest) {

        List<Program> programs = programService.getAll(securityService.getUser());
        return ResponseUtils.getQueryResponse(programs, programQueryMapper, searchRequest, queryParams);
    }

    @Get("/programs/{programId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    @AddMetadata
    public HttpResponse<Response<Program>> getProgram(@PathVariable UUID programId) {

        Optional<Program> program = programService.getById(programId);
        if(program.isPresent()) {
            Response<Program> response = new Response(program.get());
            return HttpResponse.ok(response);
        } else {
            return HttpResponse.notFound();
        }
    }

    @Post("/programs")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured("SYSTEM ADMINISTRATOR")
    @AddMetadata
    public HttpResponse<Response<Program>> createProgram(@Valid @Body ProgramRequest programRequest) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            Program program = programService.create(programRequest, actingUser);
            Response<Program> response = new Response(program);
            return HttpResponse.ok(response);
        } catch (AlreadyExistsException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch (UnprocessableEntityException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @Put("/programs/{programId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN, ProgramSecuredRole.SYSTEM_ADMIN})
    @AddMetadata
    public HttpResponse<Response<Program>> updateProgram(@PathVariable UUID programId, @Valid @Body ProgramRequest programRequest) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            Program program = programService.update(programId, programRequest, actingUser);
            Response<Program> response = new Response(program);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (UnprocessableEntityException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @Delete("/programs/archive/{programId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured("SYSTEM ADMINISTRATOR")
    @AddMetadata
    public HttpResponse archiveProgram(@PathVariable UUID programId) {
        /* Archive a program */
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            programService.archive(programId, actingUser);
            return HttpResponse.ok();
        } catch(DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    @Get("/programs/{programId}/users{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<ProgramUser>>> getProgramUsers(
            @PathVariable UUID programId,
            @QueryValue @QueryValid(using = ProgramUserQueryMapper.class) @Valid QueryParams queryParams) {

        try {
            List<ProgramUser> programUsers = programUserService.getProgramUsers(programId);
            return ResponseUtils.getQueryResponse(programUsers, programUserQueryMapper, queryParams);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    @Post("/programs/{programId}/users/search{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<ProgramUser>>> searchProgramUsers(
            @PathVariable UUID programId,
            @QueryValue @QueryValid(using = ProgramUserQueryMapper.class) @Valid QueryParams queryParams,
            @Body @SearchValid(using= ProgramUserQueryMapper.class) SearchRequest searchRequest) {

        try {
            List<ProgramUser> programUsers = programUserService.getProgramUsers(programId);
            return ResponseUtils.getQueryResponse(programUsers, programUserQueryMapper, searchRequest, queryParams);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    @Get("/programs/{programId}/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    @AddMetadata
    public HttpResponse<Response<ProgramUser>> getProgramUser(@PathVariable UUID programId, @PathVariable UUID userId) {

        Optional<ProgramUser> programUser = programUserService.getProgramUserbyId(programId, userId);

        if(programUser.isPresent()) {
            Response<ProgramUser> response = new Response(programUser.get());
            return HttpResponse.ok(response);
        } else {
            log.info("Program user not found");
            return HttpResponse.notFound();
        }
    }

    @Post("/programs/{programId}/users")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN, ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse<Response<ProgramUser>> addProgramUser(@PathVariable UUID programId, @Valid @Body ProgramUserRequest programUserRequest) {
        /* Add a user to a program. Create the user if they don't exist. */

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ProgramUser programUser = programUserService.addProgramUser(actingUser, programId, programUserRequest);
            Response<ProgramUser> response = new Response<>(programUser);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AlreadyExistsException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch (UnprocessableEntityException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @Put("/programs/{programId}/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.SYSTEM_ADMIN, ProgramSecuredRole.PROGRAM_ADMIN})
    public HttpResponse<Response<ProgramUser>> updateProgramUser(@PathVariable UUID programId, @PathVariable UUID userId,
                                                                 @Valid @Body ProgramUserRequest programUserRequest) {
        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ProgramUser programUser = programUserService.editProgramUser(actingUser, programId, userId, programUserRequest);
            Response response = new Response(programUser);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AlreadyExistsException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch (UnprocessableEntityException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (ForbiddenException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @Delete("/programs/{programId}/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN, ProgramSecuredRole.SYSTEM_ADMIN})
    public HttpResponse archiveProgramUser(@PathVariable UUID programId, @PathVariable UUID userId) {

        try {
            programUserService.archiveProgramUser(programId, userId);
            return HttpResponse.ok();
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    @Get("/programs/{programId}/locations{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<ProgramLocation>>> getProgramLocations(
            @PathVariable UUID programId,
            @QueryValue @QueryValid(using= ProgramLocationQueryMapper.class) @Valid QueryParams queryParams) {

        try {
            List<ProgramLocation> programLocations = programLocationService.getByProgramId(programId);
            return ResponseUtils.getQueryResponse(programLocations, programLocationQueryMapper, queryParams);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (ApiException e) {
            log.error("Error fetching program locations: " + Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError();
        }
    }

    @Post("/programs/{programId}/locations/search{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<ProgramLocation>>> postProgramLocationsSearch(
            @PathVariable UUID programId,
            @QueryValue @QueryValid(using= ProgramLocationQueryMapper.class) @Valid QueryParams queryParams,
            @Body @SearchValid(using= ProgramLocationQueryMapper.class) SearchRequest searchRequest ) {

        try {
            List<ProgramLocation> programLocations = programLocationService.getByProgramId(programId);
            return ResponseUtils.getQueryResponse(programLocations, programLocationQueryMapper, searchRequest, queryParams);

        } catch (DoesNotExistException e){
            log.error(e.getMessage(), e);
            return HttpResponse.notFound();
        } catch (ApiException e) {
            log.error("Error fetching program locations: " + Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError();
        }

    }


    @Get("/programs/{programId}/locations/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<ProgramLocation>> getProgramLocations(@PathVariable UUID programId,
                                                                       @PathVariable UUID locationId) {

        Optional<ProgramLocation> programLocation = null;
        try {
            programLocation = programLocationService.getById(programId, locationId);
        } catch (ApiException e) {
            log.error("Error fetching program location: " + Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError();
        }

        if(programLocation.isPresent()) {
            Response<ProgramLocation> response = new Response(programLocation.get());
            return HttpResponse.ok(response);
        } else {
            log.info("Program location not found");
            return HttpResponse.notFound();
        }
    }

    @Post("/programs/{programId}/locations")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN})
    public HttpResponse<Response<ProgramLocation>> addProgramLocation(@PathVariable UUID programId,
                                                                      @Valid @Body ProgramLocationRequest locationRequest) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ProgramLocation programLocation = programLocationService.create(actingUser, programId, locationRequest);
            Response<ProgramLocation> response = new Response(programLocation);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (MissingRequiredInfoException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (UnprocessableEntityException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @Put("/programs/{programId}/locations/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN})
    public HttpResponse<Response<Program>> updateProgramLocation(@PathVariable UUID programId,
                                                                 @PathVariable UUID locationId,
                                                                 @Valid @Body ProgramLocationRequest locationRequest) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            ProgramLocation location = programLocationService.update(actingUser, programId, locationId, locationRequest);
            Response<Program> response = new Response(location);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (MissingRequiredInfoException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (UnprocessableEntityException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @Delete("/programs/{programId}/locations/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN})
    public HttpResponse archiveProgramLocation(@PathVariable UUID programId,
                                              @PathVariable UUID locationId) {

         try {
            AuthenticatedUser actingUser = securityService.getUser();
            programLocationService.archive(actingUser, programId, locationId);
            return HttpResponse.ok();
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    @Get("/programs/{programId}/observation-levels")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<ProgramObservationLevel>>> getProgramObservationLevels(@PathVariable UUID programId)
            throws DoesNotExistException {
        List<ProgramObservationLevel> programObservationLevels = programObservationLevelService.getByProgramId(programId);

        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        Pagination pagination = new Pagination(programObservationLevels.size(), 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<ProgramObservationLevel>> response = new Response(metadata, new DataResponse<>(programObservationLevels));
        return HttpResponse.ok(response);
    }

}
