package org.breedinginsight.api.bi.v1.controller;

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
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import com.google.gson.Gson;

import javax.inject.Inject;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

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
        this.gson = new Gson();
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
    public HttpResponse users(Principal principal, @PathVariable Integer userId) {

        return HttpResponse.ok("get user");
    }

    @Produces(MediaType.TEXT_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse users(Principal principal) {

        return HttpResponse.ok("get user list");
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse createUser(Principal principal, @Body UserRequest user){

        return HttpResponse.ok("create user");
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse updateUser(Principal principal, @Body UserRequest user){

        return HttpResponse.ok("update user");
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse deleteUser(Principal principal, @PathVariable Integer userId){

        return HttpResponse.ok("delete user");
    }
}
