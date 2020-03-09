package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.*;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.model.Location;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.exception.DataAccessException;

import javax.inject.Inject;
import javax.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class ProgramController {

    @Inject
    private ProgramService programService;
    @Inject
    private UserService userService;
    @Inject
    private ProgramUserService programUserService;

    @Get("/programs")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<Program>>> getPrograms() {

        try {
            List<Program> programs = programService.getAll();

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(programs.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response<DataResponse<Program>> response = new Response(metadata, new DataResponse<>(programs));
            return HttpResponse.ok(response);
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Get("/programs/{programId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Program>> getProgram(@PathVariable UUID programId) {

        try {
            Program program = programService.getById(programId);
            Response<Program> response = new Response(program);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Post("/programs")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Program>> createProgram(Principal principal, @Valid @Body ProgramRequest programRequest) {

        try {
            String orcid = principal.getName();
            User user = userService.getByOrcid(orcid);
            Program program = programService.create(programRequest, user);
            Response<Program> response = new Response(program);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Put("/programs/{programId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Program>> updateProgram(Principal principal, @PathVariable UUID programId, @Valid @Body ProgramRequest programRequest) {

        try {
            String orcid = principal.getName();
            User user = userService.getByOrcid(orcid);
            Program program = programService.update(programId, programRequest, user);
            Response<Program> response = new Response(program);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Delete("/programs/archive/{programId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse archiveProgram(Principal principal, @PathVariable UUID programId) {
        /* Archive a program */
        try {
            String orcid = principal.getName();
            User user = userService.getByOrcid(orcid);
            programService.archive(programId, user);
        } catch(DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch(DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
        return HttpResponse.ok();
    }

    @Get("/programs/{programId}/users")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<ProgramUser>> getProgramUsers(@PathVariable UUID programId) {

        try {
            List<ProgramUser> programUsers = programUserService.getProgramUsers(programId);

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(programUsers.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response response = new Response(metadata, new DataResponse<>(programUsers));
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Get("/programs/{programId}/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @AddMetadata
    public HttpResponse<Response<ProgramUser>> getProgramUser(@PathVariable UUID programId, @PathVariable UUID userId) {

        try {
            ProgramUser programUser = programUserService.getProgramUserbyId(programId, userId);
            Response<ProgramUser> response = new Response<>(programUser);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Post("/programs/{programId}/users")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<ProgramUser>> addProgramUser(Principal principal, @PathVariable UUID programId, @Valid @Body ProgramUserRequest programUserRequest) {
        /* Add a user to a program. Create the user if they don't exist. */

        try {
            String orcid = principal.getName();
            User user = userService.getByOrcid(orcid);
            ProgramUser programUser = programUserService.addProgramUser(user, programId, programUserRequest);
            Response<ProgramUser> response = new Response<>(programUser);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AlreadyExistsException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Put("/programs/{programId}/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<ProgramUser>> updateProgramUser(Principal principal, @PathVariable UUID programId, @PathVariable UUID userId,
                                                                 @Valid @Body ProgramUserRequest programUserRequest) {
        /* Add a user to a program. Create the user if they don't exist. */

        try {
            String orcid = principal.getName();
            User user = userService.getByOrcid(orcid);
            ProgramUser programUser = programUserService.editProgramUser(user, programId, programUserRequest);
            Response response = new Response(programUser);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AlreadyExistsException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }



    @Delete("/programs/{programId}/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse removeProgramUser(@PathVariable UUID programId, @PathVariable UUID userId) {

        try {
            programUserService.removeProgramUser(programId, userId);
            return HttpResponse.ok();
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Get("/programs/{programId}/locations")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<Location>>> getProgramLocations(@PathVariable UUID programId) {

        try {
            List<Location> programLocations = programService.getProgramLocations(programId);

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(programLocations.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response response = new Response(metadata, new DataResponse<>(programLocations));
            return HttpResponse.ok(response);

        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Get("/programs/{programId}/locations/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @AddMetadata
    public HttpResponse<Response<Location>> getProgramLocations(@PathVariable UUID programId, @PathVariable UUID locationId) {

        try {
            Location programLocation = programService.getProgramLocation(programId, locationId);
            Response response = new Response(programLocation);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Post("/programs/{programId}/locations")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Location>> addProgramLocation(@PathVariable UUID programId, @Valid @Body ProgramLocationRequest locationRequest) {

        try {
            Location programLocation = programService.addProgramLocation(programId, locationRequest);
            Response response = new Response(programLocation);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AlreadyExistsException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Delete("/programs/{programId}/locations/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse removeProgramLocation(@PathVariable UUID programId, @PathVariable UUID locationId) {
        try {
            programService.removeProgramLocation(programId, locationId);
            return HttpResponse.ok();
        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

}
