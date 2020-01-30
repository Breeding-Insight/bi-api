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
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;

import javax.inject.Inject;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.breedinginsight.api.model.v1.response.UserInfoResponse;
import org.jooq.exception.DataAccessException;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class UserController {

    @Inject
    private UserService userService;

    @Get("/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<UserInfoResponse>> userinfo(Principal principal) {

        try {

            String orcid = principal.getName();
            UserInfoResponse userInfoResponse = userService.get(orcid);
            List<Status> metadataStatus = new ArrayList<>();
            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Authentication Successful"));
            // Construct our metadata and response
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<UserInfoResponse> response = new Response<>(metadata, userInfoResponse);
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
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<UserInfoResponse>> users(@PathVariable UUID userId) {

        try {

            UserInfoResponse userInfoResponse = userService.get(userId);
            List<Status> metadataStatus = new ArrayList<>();
            // Parse into our java object
            List<String> roles = new ArrayList<>();
            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            // Construct our metadata and response
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<UserInfoResponse> response = new Response<>(metadata, userInfoResponse);
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
    public HttpResponse<Response<DataResponse<UserInfoResponse>>> users() {

        try {

            List<UserInfoResponse> users = userService.getAll();
            //TODO: Add in pagination
            List<Status> metadataStatus = new ArrayList<>();
            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            // Construct our metadata and response
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(users.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<UserInfoResponse>> response = new Response<>(metadata, new DataResponse<>(users));
            return HttpResponse.ok(response);

        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Post("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<UserInfoResponse>> createUser(@Body UserRequest user){

        try {

            UserInfoResponse userInfoResponse = userService.create(user);
            List<Status> metadataStatus = new ArrayList<>();
            // User is now created successfully
            metadataStatus.add(new Status(StatusCode.INFO, "User created successfully"));
            // Construct our metadata and response
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<UserInfoResponse> response = new Response<>(metadata, userInfoResponse);
            return HttpResponse.ok(response);

        } catch (MissingRequiredInfoException e) {
            log.info(e.getMessage());
            return HttpResponse.badRequest();
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
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<UserInfoResponse>> updateUser(@PathVariable UUID userId, @Body UserRequest user){

        try {

            UserInfoResponse userInfoResponse = userService.update(userId, user);
            List<Status> metadataStatus = new ArrayList<>();
            // Our user is updated successfully
            metadataStatus.add(new Status(StatusCode.INFO, "User updated successfully"));
            // Construct our metadata and response
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<UserInfoResponse> response = new Response<>(metadata, userInfoResponse);
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
