package org.breedinginsight.api.v1.controller;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.util.UUID;

@Produces
@Singleton
@Slf4j
@Requires(classes = {Exception.class, ExceptionHandler.class})
public class InternalServerErrorHandler implements ExceptionHandler<Exception, HttpResponse> {

    @Override
    public HttpResponse handle(HttpRequest request, Exception e) {
        UUID errorId = UUID.randomUUID();
        String logErrorMsg = String.format("Error Id: %s", errorId);
        log.error(logErrorMsg, e);
        return HttpResponse.serverError(logErrorMsg);
    }
}
