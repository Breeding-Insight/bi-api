package org.breedinginsight.api.v1.controller.brapi;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.*;
import junit.framework.AssertionFailedError;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.ResponseHandlerFunction;
import org.brapi.client.v2.model.BrAPIRequest;
import org.breedinginsight.services.brapi.BrAPIClientProvider;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.dao.db.tables.daos.*;
import org.breedinginsight.dao.db.tables.pojos.*;
import org.breedinginsight.model.*;
import org.breedinginsight.services.ProgramService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.util.Optional;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// These tests are a little hacky, but should be dependable.
// Spies on the BrAPIClient to check the url at the time the brapi service is called.
// Because of that, the assert is checked in the micronaut scope and assertions failures are
// handled as server errors. So, we need to check the logs to see if an assertion error was thrown.
// TODO: This could be made less hacky if there BrAPICLient returned actual data. Right now we expect
// the tests to fail so we handle the internal server exception. If they were supposed to fail, the
// only errors would be assertion failure errors.

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrAPIServerFilterIntegrationTest {

    private ProgramEntity validProgram;
    private TraitEntity validVariable;

    private ListAppender<ILoggingEvent> loggingEventListAppender;
    @Inject
    private ProgramService programService;
    @MockBean(ProgramService.class)
    ProgramService programService() { return mock(ProgramService.class);}
    @Inject
    private BrAPIClientProvider brAPIClientProvider;
    @MockBean(BrAPIClientProvider.class)
    BrAPIClientProvider brAPIClientProvider() { return mock(BrAPIClientProvider.class, CALLS_REAL_METHODS); }

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private TraitDao traitDao;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @BeforeAll
    public void setup() {

        insertTestData();
        retrieveTestData();
    }

    @BeforeEach
    public void resetLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();
        logger.addAppender(loggingEventListAppender);
        this.loggingEventListAppender = loggingEventListAppender;
    }

    public void insertTestData() {
        // Insert our traits into the db
        var fp = FannyPack.fill("src/test/resources/sql/TraitControllerIntegrationTest.sql");

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert program observation level
        dsl.execute(fp.get("InsertProgramObservationLevel"));

        // Insert program ontology sql
        dsl.execute(fp.get("InsertProgramOntology"));

        // Insert method
        dsl.execute(fp.get("InsertMethod"));

        // Insert scale
        dsl.execute(fp.get("InsertScale"));

        // Insert trait
        dsl.execute(fp.get("InsertTrait"));
    }

    public void retrieveTestData() {
        // Retrieve our new data
        validProgram = programDao.findAll().get(0);
        validVariable = traitDao.findAll().get(0);
    }

    @Test
    @Order(1)
    public void urlChangesForDifferentRequestsCallOne() {
        // Checks that two requests sent to the same micronaut instance will have different
        // urls. Could use some improvements

        String coreUrl = "http://core-test" + UUID.randomUUID().toString();
        String phenoUrl = "http://pheno-test" + UUID.randomUUID().toString();
        String genoUrl = "http://geno-test" + UUID.randomUUID().toString();
        ProgramBrAPIEndpoints programBrAPIEndpoints = getBrAPIEndpoints(coreUrl, phenoUrl, genoUrl);

        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(programBrAPIEndpoints);
        when(programService.exists(any(UUID.class))).thenReturn(true);

        // Assert our brapi url was used
        checkBrAPIClientExecution(phenoUrl);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus(), "Response status is incorrect");

        // Check that our error is not an assertion error
        assertLogEvent(InternalServerException.class.getName());
    }

    @Test
    @Order(2)
    public void urlChangesForDifferentRequestsCallTwo() {

        String coreUrl1 = "http://core-test" + UUID.randomUUID().toString();
        String phenoUrl1 = "http://pheno-test" + UUID.randomUUID().toString();
        String genoUrl1 = "http://geno-test" + UUID.randomUUID().toString();
        ProgramBrAPIEndpoints programBrAPIEndpoints = getBrAPIEndpoints(coreUrl1, phenoUrl1, genoUrl1);

        reset(programService);
        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(programBrAPIEndpoints);
        when(programService.exists(any(UUID.class))).thenReturn(true);

        // Assert our brapi url was used
        checkBrAPIClientExecution(phenoUrl1);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        // Expect error because brapi results will not be returned
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus(), "Response status is incorrect");

        // Check that our error is not an assertion error
        assertLogEvent(InternalServerException.class.getName());
    }

    @Test
    public void getTraitsUsesFilter() {

        String phenoUrl = "http://getTraits" + UUID.randomUUID().toString();
        ProgramBrAPIEndpoints programBrAPIEndpoints = getBrAPIEndpoints("", phenoUrl, "");

        reset(programService);
        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(programBrAPIEndpoints);
        when(programService.exists(any(UUID.class))).thenReturn(true);

        checkBrAPIClientExecution(phenoUrl);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        // Expect error because brapi results will not be returned
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus(), "Response status is incorrect");

        assertLogEvent(InternalServerException.class.getName());
    }

    @Test
    public void getTraitSingleUsesFilter() {

        String phenoUrl = "http://getTraitSingle" + UUID.randomUUID().toString();
        ProgramBrAPIEndpoints programBrAPIEndpoints = getBrAPIEndpoints(null, phenoUrl, null);

        reset(programService);
        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(programBrAPIEndpoints);
        when(programService.exists(any(UUID.class))).thenReturn(true);

        checkBrAPIClientExecution(phenoUrl);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validVariable.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        // Expect error because brapi results will not be returned
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus(), "Response status is incorrect");

        assertLogEvent(InternalServerException.class.getName());
    }

    public void assertLogEvent(String failureClass) {
        // Our other assertions are thrown inside the micronaut code. We need to catch them in the logs
        String assertionClassName = org.opentest4j.AssertionFailedError.class.getName();
        for (ILoggingEvent loggingEvent: loggingEventListAppender.list){
            assertEquals(failureClass, loggingEvent.getThrowableProxy().getClassName(), "Was not expected failure reason");
        }
    }

    public ProgramBrAPIEndpoints getBrAPIEndpoints(String coreUrl, String phenoUrl, String genoUrl){
        return ProgramBrAPIEndpoints.builder()
                .coreUrl(coreUrl == null ? Optional.empty() : Optional.of(coreUrl))
                .genoUrl(genoUrl == null ? Optional.empty() : Optional.of(genoUrl))
                .phenoUrl(phenoUrl == null ? Optional.empty() : Optional.of(phenoUrl))
                .build();
    }

    public void checkBrAPIClientExecution(String expectedUrl) throws AssertionFailedError {

        // Takes advantage of brapi library code to mimic no return results from api.
        Answer<Optional<Object>> checkBrAPIExecution = new Answer<Optional<Object>>() {
            @Override
            public Optional<Object> answer(InvocationOnMock invocation) throws AssertionFailedError {
                BrAPIClient executingBrAPIClient = (BrAPIClient) invocation.getMock();
                // Check that our url is correct
                assertEquals(expectedUrl, executingBrAPIClient.brapiURI(), "Expected url was not used");
                return Optional.empty();
            }
        };

        // Test the correct url was set for our provider
        reset(brAPIClientProvider);
        when(brAPIClientProvider.getClient(BrAPIClientType.PHENO)).thenAnswer(new Answer<BrAPIClient>() {
            @Override
            public BrAPIClient answer(InvocationOnMock invocation) throws Throwable {
                // Create a spy on our real brapi client to see what url was ultimately used
                BrAPIClient realBrAPIClient = (BrAPIClient) invocation.callRealMethod();
                BrAPIClient brAPIClientSpy = spy(realBrAPIClient);
                doAnswer(checkBrAPIExecution)
                        .when(brAPIClientSpy)
                        .execute(any(BrAPIRequest.class), any(ResponseHandlerFunction.class));

                return brAPIClientSpy;
            }
        });

    }

}
