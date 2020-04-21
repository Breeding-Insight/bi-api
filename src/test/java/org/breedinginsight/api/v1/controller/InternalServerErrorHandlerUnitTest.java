package org.breedinginsight.api.v1.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import org.breedinginsight.services.ProgramService;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.util.Optional;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InternalServerErrorHandlerUnitTest {

    ListAppender<ILoggingEvent> loggingEventListAppender;

    @Inject
    ProgramService programService;
    @Inject
    ProgramController programController;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @MockBean(ProgramController.class)
    ProgramController programController() {
        return mock(ProgramController.class);
    }

    @MockBean(ProgramService.class)
    ProgramService programService() {
        return mock(ProgramService.class);
    }

    @BeforeEach
    void setupErrorLogger() {

        Logger logger = (Logger) LoggerFactory.getLogger(InternalServerErrorHandler.class);
        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();
        logger.addAppender(loggingEventListAppender);
        this.loggingEventListAppender = loggingEventListAppender;
    }

    @Test
    public void getProgramsInternalServerError() {

        when(programController.getPrograms()).thenThrow(new DataAccessException("Query 123 failed"));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus(), "Response status is incorrect");

        // Check that we can find our error id in logs
        String errorId = e.getResponse().body().toString();
        ILoggingEvent loggingEvent = loggingEventListAppender.list.get(0);
        assertEquals(Level.ERROR, loggingEvent.getLevel(), "Wrong logging level was used");
        assertEquals(errorId, loggingEvent.getMessage(), "Id returned doesn't match logging id");

    }

    @Test
    public void controllerHandledExceptionIgnored() {

        when(programService.getById(any(UUID.class))).thenReturn(Optional.empty());

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + UUID.randomUUID()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus(), "Response status is incorrect");

        assertEquals(0, loggingEventListAppender.list.size(), "Logs were entered, but shouldn't have been.");
    }
}
