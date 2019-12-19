package org.breedinginsight.api.controller;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

@Controller
public class HelloWorld {

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

    @Get("/unauthorized")
    @Produces(MediaType.TEXT_PLAIN)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public String unauthorized() {
        return "Unauthorized!";
    }
}
