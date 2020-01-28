package org.breedinginsight.api.v1.controller;

import com.google.gson.GsonBuilder;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.annotation.Delete;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.dao.db.tables.records.BiUserRecord;
import org.jooq.*;
import com.google.gson.Gson;

import javax.inject.Inject;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.BI_USER;
import org.breedinginsight.api.model.v1.response.UserInfoResponse;
import org.jooq.exception.DataAccessException;

@Slf4j
@Accessors(fluent=true)
@Controller("/${micronaut.bi.api.version}")
public class UserController {

    @Inject
    DSLContext dsl;
    @Getter
    private final Gson gson;

    public UserController() {
        this.gson = new GsonBuilder().serializeNulls().create();
    }

    @Get("/userinfo")
    @Produces(MediaType.TEXT_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse userinfo(Principal principal) {

        List<Status> metadataStatus = new ArrayList<>();

        String orcid = principal.getName();

        try {
            // User has been authenticated against orcid, check they have a bi account.
            BiUserRecord result = dsl.fetchOne(BI_USER, BI_USER.ORCID.eq(orcid));

            if (result == null) {
                // If they are not in our database, fail our authentication
                log.info("ORCID not associated with registered user");
                return HttpResponse.unauthorized();
            }

            // For now, if we have found a record, let them through
            UserInfoResponse userInfoResponse = new UserInfoResponse(result);

            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Authentication Successful"));

            // Construct our metadata and response
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<UserInfoResponse> response = new Response<>(metadata, userInfoResponse);

            return HttpResponse.ok(gson.toJson(response));

        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Get("/users/{userId}")
    @Produces(MediaType.TEXT_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse users(@PathVariable UUID userId) {

        List<Status> metadataStatus = new ArrayList<>();

        try {
            // User has been authenticated against orcid, check they have a bi account.
            BiUserRecord biUser = dsl.fetchOne(BI_USER, BI_USER.ID.eq(userId));

            if (biUser == null) {
                log.info("UUID for user does not exist");
                return HttpResponse.notFound();
            }

            // Parse into our java object
            List<String> roles = new ArrayList<>();

            UserInfoResponse userInfoResponse = new UserInfoResponse(biUser);

            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));

            // Construct our metadata and response
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<UserInfoResponse> response = new Response<>(metadata, userInfoResponse);

            return HttpResponse.ok(gson.toJson(response));

        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Get("/users")
    @Produces(MediaType.TEXT_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse users() {

        //TODO: Add in pagination
        List<Status> metadataStatus = new ArrayList<>();

        try {
            // Get our users
            List<BiUserRecord> results = dsl.select().from(BI_USER).fetchInto(BiUserRecord.class);

            List<UserInfoResponse> resultBody = new ArrayList<>();
            for (BiUserRecord result : results) {
                // We don't have roles right now
                List<String> roles = new ArrayList<>();
                // Generate our response class from db record
                UserInfoResponse userInfoResponse = new UserInfoResponse(result)
                        .roles(roles);

                resultBody.add(userInfoResponse);
            }

            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));

            // Construct our metadata and response
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(resultBody.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<UserInfoResponse>> response = new Response<>(metadata, new DataResponse<>(resultBody));

            return HttpResponse.ok(gson.toJson(response));

        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Post("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse createUser(@Body UserRequest user){

        List<Status> metadataStatus = new ArrayList<>();

        // Check that name and email was provided
        if (user.getEmail() == null || user.getName() == null) {
            log.info("Missing name or email");
            return HttpResponse.badRequest();
        }

        // TODO: Check is a valid email

        try {
            // Check if the users email already exists
            Integer numExistEmails = dsl.selectCount().from(BI_USER).where(BI_USER.EMAIL.eq(user.getEmail())).fetchOne(0, Integer.class);

            // Return a conflict with an 'account already exists' flag and message
            if (numExistEmails > 0) {
                log.info("Email already exists");
                return HttpResponse.status(HttpStatus.CONFLICT, "Email already exists");
            }

            // Create the user
            BiUserRecord newBiUser = dsl.newRecord(BI_USER)
                    .setEmail(user.getEmail())
                    .setName(user.getName());
            newBiUser.store();

            // TODO: Send an email to the user with a invite token

            // User is now created successfully
            metadataStatus.add(new Status(StatusCode.INFO, "User created successfully"));

            // Convert to response object
            UserInfoResponse userInfoResponse = new UserInfoResponse(newBiUser);

            // Construct our metadata and response
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<UserInfoResponse> response = new Response<>(metadata, userInfoResponse);

            return HttpResponse.ok(gson.toJson(response));
        } catch(DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Put("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse updateUser(@PathVariable UUID userId, @Body UserRequest user){

        List<Status> metadataStatus = new ArrayList<>();

        try {
            // Update the user info
            BiUserRecord biUser = dsl.fetchOne(BI_USER, BI_USER.ID.eq(userId));

            if (biUser == null) {
                log.info("UUID for user does not exist");
                return HttpResponse.notFound();
            }

            // If values are specified, update them
            if (user.getEmail() != null) {

                // Check if the requested email address already exists
                Integer numExistEmails = dsl.selectCount()
                        .from(BI_USER)
                        .where(BI_USER.EMAIL.eq(user.getEmail()).and(BI_USER.ID.ne(userId)))
                        .fetchOne(0, Integer.class);

                // Return a conflict with an 'account already exists' flag and message
                if (numExistEmails > 0) {
                    log.info("Email already exists");
                    return HttpResponse.status(HttpStatus.CONFLICT, "Email already exists");
                }

                biUser.setEmail(user.getEmail());
            }

            if (user.getName() != null) {
                biUser.setName(user.getName());
            }

            // Store our record
            biUser.store();

            // Our user is updated successfully
            metadataStatus.add(new Status(StatusCode.INFO, "User updated successfully"));

            // Get our updated record
            biUser.refresh();

            // Convert to return object
            UserInfoResponse userInfoResponse = new UserInfoResponse(biUser);

            // Construct our metadata and response
            Pagination pagination = new Pagination(1, 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<UserInfoResponse> response = new Response<>(metadata, userInfoResponse);

            return HttpResponse.ok(gson.toJson(response));

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
            // Delete the user
            BiUserRecord biUser = dsl.fetchOne(BI_USER, BI_USER.ID.eq(userId));

            if (biUser == null) {
                log.info("UUID for user does not exist");
                return HttpResponse.notFound();
            }

            biUser.delete();
            return HttpResponse.ok();

        } catch (DataAccessException e) {
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

}
