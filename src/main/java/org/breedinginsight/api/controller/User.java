package org.breedinginsight.api.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import net.minidev.json.JSONObject;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;

import javax.inject.Inject;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.breedinginsight.dao.db.Tables.BI_USER;

@Controller
public class User {

    @Inject
    DSLContext dsl;

    @Get("/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse index(Principal principal) {
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
            JSONObject resultBody = new JSONObject();
            resultBody.put("orcid", orcid);
            resultBody.put("name", String.format("%s %s",
                    userRecord.get(BI_USER.FIRST_NAME), userRecord.get(BI_USER.LAST_NAME)));
            resultBody.put("given_name", userRecord.get(BI_USER.FIRST_NAME));
            resultBody.put("family_name", userRecord.get(BI_USER.LAST_NAME));
            resultBody.put("roles", roles);

            response = HttpResponse.ok(resultBody.toJSONString());
        }
        else {
            // If they are not in our database, fail our authentication
            response = HttpResponse.unauthorized();
        }

        return response;
    }
}
