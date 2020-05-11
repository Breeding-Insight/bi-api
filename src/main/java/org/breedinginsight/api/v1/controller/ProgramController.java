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
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramLocationService;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.ProgramUserService;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Inject;
import javax.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    @Inject
    private ProgramLocationService programLocationService;

    @Get("/programs")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<Program>>> getPrograms() {

        List<Program> programs = programService.getAll();

        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        //TODO: Put in the actual page size
        Pagination pagination = new Pagination(programs.size(), 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<Program>> response = new Response(metadata, new DataResponse<>(programs));
        return HttpResponse.ok(response);
    }

    @Get("/programs/{programId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
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
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Program>> createProgram(Principal principal, @Valid @Body ProgramRequest programRequest) {

        try {
            String orcid = principal.getName();
            Optional<User> user = userService.getByOrcid(orcid);
            if (user.isPresent()){
                Program program = programService.create(programRequest, user.get());
                Response<Program> response = new Response(program);
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.unauthorized();
            }
        } catch (UnprocessableEntityException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    @Put("/programs/{programId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Program>> updateProgram(Principal principal, @PathVariable UUID programId, @Valid @Body ProgramRequest programRequest) {

        try {
            String orcid = principal.getName();
            Optional<User> user = userService.getByOrcid(orcid);
            if (user.isPresent()){
                Program program = programService.update(programId, programRequest, user.get());
                Response<Program> response = new Response(program);
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.unauthorized();
            }
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
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse archiveProgram(Principal principal, @PathVariable UUID programId) {
        /* Archive a program */
        try {
            String orcid = principal.getName();
            Optional<User> user = userService.getByOrcid(orcid);
            if (user.isPresent()){
                programService.archive(programId, user.get());
                return HttpResponse.ok();
            } else {
                return HttpResponse.unauthorized();
            }
        } catch(DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
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
        }
    }

    @Get("/programs/{programId}/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
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
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<ProgramUser>> addProgramUser(Principal principal, @PathVariable UUID programId, @Valid @Body ProgramUserRequest programUserRequest) {
        /* Add a user to a program. Create the user if they don't exist. */

        try {
            String orcid = principal.getName();
            Optional<User> user = userService.getByOrcid(orcid);
            if (user.isPresent()){
                ProgramUser programUser = programUserService.addProgramUser(user.get(), programId, programUserRequest);
                Response<ProgramUser> response = new Response<>(programUser);
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.unauthorized();
            }
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
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<ProgramUser>> updateProgramUser(Principal principal, @PathVariable UUID programId, @PathVariable UUID userId,
                                                                 @Valid @Body ProgramUserRequest programUserRequest) {
        try {
            String orcid = principal.getName();
            Optional<User> user = userService.getByOrcid(orcid);
            if (user.isPresent()){
                ProgramUser programUser = programUserService.editProgramUser(user.get(), programId, programUserRequest);
                Response response = new Response(programUser);
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.unauthorized();
            }
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
        }
    }

    @Get("/programs/{programId}/locations")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<ProgramLocation>>> getProgramLocations(@PathVariable UUID programId) {

        try {
            List<ProgramLocation> programLocations = programLocationService.getByProgramId(programId);

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(programLocations.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response<DataResponse<ProgramLocation>> response = new Response(metadata, new DataResponse<>(programLocations));
            return HttpResponse.ok(response);

        } catch (DoesNotExistException e){
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }
    }

    @Get("/programs/{programId}/locations/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @AddMetadata
    public HttpResponse<Response<ProgramLocation>> getProgramLocations(@PathVariable UUID programId,
                                                                       @PathVariable UUID locationId) {

        Optional<ProgramLocation> programLocation = programLocationService.getById(locationId);

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
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<ProgramLocation>> addProgramLocation(Principal principal,
                                                                      @PathVariable UUID programId,
                                                                      @Valid @Body ProgramLocationRequest locationRequest) {

        String orcid = principal.getName();
        Optional<User> user = userService.getByOrcid(orcid);
        if (user.isPresent()) {
            try {
                ProgramLocation programLocation = programLocationService.create(user.get(), programId, locationRequest);
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
        else {
            return HttpResponse.unauthorized();
        }
    }

    @Put("/programs/{programId}/locations/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Program>> updateProgramLocation(Principal principal,
                                                                 @PathVariable UUID programId,
                                                                 @PathVariable UUID locationId,
                                                                 @Valid @Body ProgramLocationRequest locationRequest) {

        String orcid = principal.getName();
        Optional<User> user = userService.getByOrcid(orcid);
        if (user.isPresent()) {
            try {
                ProgramLocation location = programLocationService.update(user.get(), programId, locationId, locationRequest);
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

        } else {
            return HttpResponse.unauthorized();
        }
    }

    @Delete("/programs/{programId}/locations/{locationId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse archiveProgramLocation(Principal principal,
                                              @PathVariable UUID programId,
                                              @PathVariable UUID locationId) {


        String orcid = principal.getName();
        Optional<User> user = userService.getByOrcid(orcid);
        if (user.isPresent()) {
            try {
                programLocationService.archive(user.get(), locationId);
                return HttpResponse.ok();
            } catch (DoesNotExistException e){
                log.info(e.getMessage());
                return HttpResponse.notFound();
            }
        }
        else {
            return HttpResponse.unauthorized();
        }
    }

}
