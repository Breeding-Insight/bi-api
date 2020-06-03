package org.breedinginsight.api.v1.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.*;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.services.ProgramService;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BrAPIServerFilterUnitTest {

    UUID validUUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    ListAppender<ILoggingEvent> loggingEventListAppender;
    @Inject
    ProgramService programService;
    @MockBean(ProgramService.class)
    ProgramService programService() { return mock(ProgramService.class);}

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    @BeforeAll
    public void setup() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();
        logger.addAppender(loggingEventListAppender);
        this.loggingEventListAppender = loggingEventListAppender;
    }

    @Test
    @Order(1)
    public void urlChangesForDifferentRequests() {
        // Checks that two requests sent to the same micronaut instance will have different
        // urls.
        // Brittle test. Relies on checking the error logs for a mentioned url. Might be able to
        // be improved.

        String coreUrl = "core-test" + UUID.randomUUID().toString();
        String phenoUrl = "pheno-test" + UUID.randomUUID().toString();
        String genoUrl = "geno-test" + UUID.randomUUID().toString();
        ProgramBrAPIEndpoints programBrAPIEndpoints = getBrAPIEndpoints(coreUrl, phenoUrl, genoUrl);

        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(programBrAPIEndpoints);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("programs/" + validUUID + "/traits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus(), "Response status is incorrect");

        // Check that our incorrect url was referenced
        String errorId = e.getResponse().body().toString();
        String logMsg = getLogEvent(errorId);

        assertEquals(true, logMsg.contains(phenoUrl), "Wrong endpoint contacted");

        String coreUrl1 = "core-test" + UUID.randomUUID().toString();
        String phenoUrl1 = "pheno-test" + UUID.randomUUID().toString();
        String genoUrl1 = "geno-test" + UUID.randomUUID().toString();
        ProgramBrAPIEndpoints programBrAPIEndpoints1 = getBrAPIEndpoints(coreUrl1, phenoUrl1, genoUrl1);

        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(programBrAPIEndpoints1);

        Flowable<HttpResponse<String>> call1 = client.exchange(
                GET("programs/" + validUUID + "/traits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e1 = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus(), "Response status is incorrect");

        String errorId1 = e1.getResponse().body().toString();
        String logMsg1 = getLogEvent(errorId1);

        // Check that our incorrect url was referenced
        assertEquals(true, logMsg1.contains(phenoUrl1), "Wrong endpoint contacted");
    }

    public String getLogEvent(String expectedMsg) {

        List<ILoggingEvent> loggingEvents = loggingEventListAppender.list.stream().filter(loggingEvent -> {
            return loggingEvent.getMessage().equals(expectedMsg);
        }).collect(Collectors.toList());
        ILoggingEvent apiErrorEvent = loggingEvents.get(0);
        return apiErrorEvent.getThrowableProxy().getMessage();
    }

    public ProgramBrAPIEndpoints getBrAPIEndpoints(String coreUrl, String phenoUrl, String genoUrl){
        return ProgramBrAPIEndpoints.builder()
                .coreUrl(Optional.of("http://" + coreUrl))
                .genoUrl(Optional.of("http://" + genoUrl))
                .phenoUrl(Optional.of("http://" + phenoUrl))
                .build();
    }
}
