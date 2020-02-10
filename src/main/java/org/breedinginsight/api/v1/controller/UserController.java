package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.annotation.Delete;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.FilterMetadata;
import org.breedinginsight.model.User;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jooq.exception.DataAccessException;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class UserController {

    @Inject
    private UserService userService;

    @Get("/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @FilterMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> userinfo(Principal principal) {

        try {

            String orcid = principal.getName();
            User user = userService.getByOrcid(orcid);
            Response<User> response = new Response<>(user);
            return HttpResponse.ok(response);

        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.unauthorized();
        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Get("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @FilterMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> users(@PathVariable UUID userId) {

        try {

            User user = userService.getById(userId);
            Response<User> response = new Response<>(user);
            return HttpResponse.ok(response);

        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Get("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<User>>> users() {

        try {

            List<User> users = userService.getAll();
            //TODO: Add in pagination
            List<Status> metadataStatus = new ArrayList<>();
            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            // Construct our metadata and response
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(users.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<User>> response = new Response<>(metadata, new DataResponse<>(users));
            return HttpResponse.ok(response);

        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Post("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @FilterMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> createUser(@Body @Valid UserRequest requestUser){

        try {

            User user = userService.create(requestUser);
            Response<User> response = new Response<>(user);
            return HttpResponse.ok(response);

        } catch (AlreadyExistsException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch(DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Put("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @FilterMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> updateUser(@PathVariable UUID userId, @Body @Valid UserRequest requestUser){

        try {

            User user = userService.update(userId, requestUser);
            Response<User> response = new Response<>(user);
            return HttpResponse.ok(response);

        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AlreadyExistsException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Delete("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse deleteUser(@PathVariable UUID userId){

        try {
            userService.delete(userId);
            return HttpResponse.ok();
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

}
