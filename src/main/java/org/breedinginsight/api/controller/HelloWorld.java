package org.breedinginsight.api.controller;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import org.jooq.*;

import javax.inject.Inject;

import static org.breedinginsight.dao.db.Tables.*;

@Controller
public class HelloWorld {

    @Inject
    DSLContext dsl;

    @Get("/hello")
    @Produces(MediaType.TEXT_PLAIN)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public String index() {

        return "Hello World";
    }

    @Get("/hello/secured")
    @Produces(MediaType.TEXT_PLAIN)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public String secured() {
        return "secured";
    }

    @Get("/hello/jooq")
    @Produces(MediaType.TEXT_PLAIN)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public String indexJooq(){

        // Example of querying data through JOOQ
        Result<Record> result = dsl.select().from(BI_USER).fetch();
        String resultText = "";
        for (Record r: result) {
            resultText += r.getValue(0);
        }
        
        return resultText;
    }

    @Get("/unauthorized")
    @Produces(MediaType.TEXT_PLAIN)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public String unauthorized() {
        return "Unauthorized!";
    }
}
