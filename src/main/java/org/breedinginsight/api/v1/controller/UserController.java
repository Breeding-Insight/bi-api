package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.server.exceptions.InternalServerException;
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
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.User;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.validation.Valid;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class UserController {

    @Inject
    private UserService userService;

    @Get("/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> userinfo(Principal principal) {

        String orcid = principal.getName();
        Optional<User> user = userService.getByOrcid(orcid);

        if (user.isPresent()) {
            Response<User> response = new Response<>(user.get());
            return HttpResponse.ok(response);
        } else {
            return HttpResponse.unauthorized();
        }
    }

    @Get("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> users(@PathVariable UUID userId) {

        Optional<User> user = userService.getById(userId);

        if(user.isPresent()) {
            Response<User> response = new Response(user.get());
            return HttpResponse.ok(response);
        } else {
            log.info("User not found");
            return HttpResponse.notFound();
        }
    }

    @Get("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<User>>> users() {

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
    }

    @Post("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> createUser(Principal principal, @Body @Valid UserRequest requestUser){

        try {
            String orcid = principal.getName();
            Optional<User> actingUser = userService.getByOrcid(orcid);
            if (actingUser.isPresent()){
                User user = userService.create(actingUser.get(), requestUser);
                Response<User> response = new Response<>(user);
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.unauthorized();
            }
        } catch (AlreadyExistsException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Put("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> updateUser(Principal principal, @PathVariable UUID userId, @Body @Valid UserRequest requestUser){

        try {
            String orcid = principal.getName();
            Optional<User> actingUser = userService.getByOrcid(orcid);
            if (actingUser.isPresent()){
                User user = userService.update(actingUser.get(), userId, requestUser);
                Response<User> response = new Response<>(user);
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.unauthorized();
            }
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AlreadyExistsException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Put("/users/{userId}/roles")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> updateSystemUserRoles(Principal principal, @PathVariable UUID userId, @Body @Valid UserRequest requestUser){

        try {
            String orcid = principal.getName();
            User actingUser = userService.getByOrcid(orcid);
            User user = userService.updateSystemRoles(actingUser, userId, requestUser);
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
        }
    }
}
