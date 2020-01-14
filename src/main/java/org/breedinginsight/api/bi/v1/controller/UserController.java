package org.breedinginsight.api.bi.v1.controller;

import com.google.gson.GsonBuilder;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.breedinginsight.api.bi.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.records.BiUserRecord;
import org.jooq.*;
import com.google.gson.Gson;

import javax.inject.Inject;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.BI_USER;
import org.breedinginsight.api.bi.model.v1.response.UserInfoResponse;

@Accessors(fluent=true)
@Controller
public class UserController {

    @Inject
    DSLContext dsl;
    @Getter
    private final Gson gson;

    // Our method names to be called in our routes
    public final static String USER_INFO_FUNCTION = "userinfo";
    public final static String USERS_FUNCTION = "users";
    public final static String CREATE_USER_FUNCTION = "createUser";
    public final static String UPDATE_USER_FUNCTION = "updateUser";
    public final static String DELETE_USER_FUNCTION = "deleteUser";


    public UserController() {
        this.gson = new GsonBuilder().serializeNulls().create();
    }

    @Produces(MediaType.TEXT_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse userinfo(Principal principal) {
        String orcid = principal.getName();

        // User has been authenticated against orcid, check they have a bi account.
        Result<Record> result = dsl.select().from(BI_USER).where(BI_USER.ORCID.eq(orcid)).fetch();

        // For now, if we have found a record, let them through
        HttpResponse response;
        if (result.size() > 0){
            // Get our first record for the user (Should be only one for the given orcid)
            Record userRecord = result.get(0);
            List<String> roles = new ArrayList<String>();

            // Construct our response JSON
            UserInfoResponse userInfoResponse = new UserInfoResponse()
                    .orcid(orcid)
                    .name(userRecord.get(BI_USER.NAME))
                    .roles(roles);

            response = HttpResponse.ok(gson.toJson(userInfoResponse));
        }
        else {
            // If they are not in our database, fail our authentication
            response = HttpResponse.unauthorized();
        }

        return response;
    }

    @Produces(MediaType.TEXT_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse users(Principal principal, @PathVariable UUID userId) {

        // User has been authenticated against orcid, check they have a bi account.
        BiUserRecord biUser = dsl.fetchOne(BI_USER, BI_USER.ID.eq(userId));

        // Parse into our java object
        List<String> roles = new ArrayList<>();
        UserInfoResponse userInfoResponse = new UserInfoResponse()
                .id(biUser.getId())
                .orcid(biUser.getOrcid())
                .name(biUser.getName())
                .roles(roles);

        return HttpResponse.ok(gson.toJson(userInfoResponse));
    }

    @Produces(MediaType.TEXT_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse users(Principal principal) {

        // User has been authenticated against orcid, check they have a bi account.
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

        return HttpResponse.ok(gson.toJson(resultBody));
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse createUser(Principal principal, @Body UserRequest user){

        // Check that name and email was provided
        if (user.getEmail() == null || user.getName() == null) { return HttpResponse.badRequest(); }

        // TODO: Check is a valid email

        // Check if the users email already exists
        Integer numExistEmails = dsl.selectCount().from(BI_USER).where(BI_USER.EMAIL.eq(user.getEmail())).fetchOne(0, Integer.class);

        // Return an okay with an 'account already exists' flag and message
        // TODO: Implement API return messages metadata
        if (numExistEmails > 0) { return HttpResponse.ok(); }

        // Create the user
        BiUserRecord newBiUser = dsl.newRecord(BI_USER)
                .setEmail(user.getEmail())
                .setName(user.getName());
        newBiUser.store();

        // Convert to response object
        UserInfoResponse userInfoResponse = new UserInfoResponse(newBiUser);

        // TODO: Send an email to the user with a invite token

        return HttpResponse.ok(gson.toJson(userInfoResponse));
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse updateUser( Principal principal, @PathVariable UUID userId, @Body UserRequest user){

        // Update the user info
        BiUserRecord biUser = dsl.fetchOne(BI_USER, BI_USER.ID.eq(userId));

        // If values are specified, update them
        if (user.getEmail() != null){  biUser.setEmail(user.getEmail()); }
        if (user.getName() != null){ biUser.setName(user.getName()); }

        // Store our record
        biUser.store();
        // Get our updated record
        biUser.refresh();

        // Convert to return object
        UserInfoResponse userInfoResponse = new UserInfoResponse(biUser);

        return HttpResponse.ok(gson.toJson(userInfoResponse));
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse deleteUser(Principal principal, @PathVariable UUID userId){

        // Delete the user
        BiUserRecord biUser = dsl.fetchOne(BI_USER, BI_USER.ID.eq(userId));
        biUser.delete();

        return HttpResponse.ok();
    }

}
