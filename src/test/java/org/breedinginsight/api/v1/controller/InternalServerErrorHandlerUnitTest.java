/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.api.v1.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.test.annotation.MockBean;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.CountryService;
import org.breedinginsight.services.AccessibilityService;
import org.breedinginsight.services.TopographyService;
import org.breedinginsight.services.EnvironmentTypeService;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.util.Optional;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InternalServerErrorHandlerUnitTest extends DatabaseTest {

    private ListAppender<ILoggingEvent> loggingEventListAppender;

    @MockBean(ProgramService.class)
    ProgramService programService() {
        return mock(ProgramService.class);
    }

    @MockBean(CountryController.class)
    CountryController countryController() {
        return mock(CountryController.class);
    }

    @MockBean(CountryService.class)
    CountryService countryService() {
        return mock(CountryService.class);
    }

    @MockBean(TopographyController.class)
    TopographyController topographyController() {
        return mock(TopographyController.class);
    }

    @MockBean(TopographyService.class)
    TopographyService topographyService() {
        return mock(TopographyService.class);
    }

    @MockBean(AccessibilityController.class)
    AccessibilityController accessibilityController() {
        return mock(AccessibilityController.class);
    }

    @MockBean(AccessibilityService.class)
    AccessibilityService accessibilityService() {
        return mock(AccessibilityService.class);
    }

    @MockBean(EnvironmentTypeController.class)
    EnvironmentTypeController environmentTypeController() {
        return mock(EnvironmentTypeController.class);
    }

    @MockBean(EnvironmentTypeService.class)
    EnvironmentTypeService environmentTypeService() {
        return mock(EnvironmentTypeService.class);
    }

    @Inject
    private ProgramService programService;
    @Inject
    private CountryService countryService;
    @Inject
    private CountryController countryController;
    @Inject
    private AccessibilityService accessibilityService;
    @Inject
    private AccessibilityController accessibilityController;
    @Inject
    private TopographyService topographyService;
    @Inject
    private TopographyController topographyController;
    @Inject
    private EnvironmentTypeService environmentTypeService;
    @Inject
    private EnvironmentTypeController environmentTypeController;
    @Inject
    private UserDAO userDAO;
    @Inject
    private DSLContext dsl;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @AfterAll
    public void finish() { super.stopContainers(); }
    
    @BeforeAll
    void setup() {
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");

        // Insert system roles
        User testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());
    }
    @BeforeEach
    @SneakyThrows
    void setupErrorLogger() {

        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(new ProgramBrAPIEndpoints());
        Logger logger = (Logger) LoggerFactory.getLogger(InternalServerErrorHandler.class);
        ListAppender<ILoggingEvent> loggingEventListAppender = new ListAppender<>();
        loggingEventListAppender.start();
        logger.addAppender(loggingEventListAppender);
        this.loggingEventListAppender = loggingEventListAppender;
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

    @Test
    public void getCountriesInternalServerError() {

        when(countryController.getCountries()).thenThrow(new DataAccessException("Query 123 failed"));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/countries").cookie(new NettyCookie("phylo-token", "test-country")), String.class
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
    public void countryControllerHandledExceptionIgnored() {

        when(countryService.getById(any(UUID.class))).thenReturn(Optional.empty());

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/countries/" + UUID.randomUUID()).cookie(new NettyCookie("phylo-token", "test-country")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus(), "Response status is incorrect");

        assertEquals(0, loggingEventListAppender.list.size(), "Logs were entered, but shouldn't have been.");
    }

    @Test
    public void getTopographiesInternalServerError() {

        when(topographyController.getTopographies()).thenThrow(new DataAccessException("Query 123 failed"));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/topography-options").cookie(new NettyCookie("phylo-token", "test-topography")), String.class
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
    public void topographyControllerHandledExceptionIgnored() {

        when(topographyService.getById(any(UUID.class))).thenReturn(Optional.empty());

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/topography-options/" + UUID.randomUUID()).cookie(new NettyCookie("phylo-token", "test-topography")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus(), "Response status is incorrect");

        assertEquals(0, loggingEventListAppender.list.size(), "Logs were entered, but shouldn't have been.");
    }

    @Test
    @Order(1)
    public void getAccessibilitiesInternalServerError() {

        //when(accessibilityController.getAccessibilities()).thenThrow(new DataAccessException("Query 123 failed"));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/accessibility-options").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
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
    public void accessibilityControllerHandledExceptionIgnored() {

        when(accessibilityService.getById(any(UUID.class))).thenReturn(Optional.empty());

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/accessibility-options/" + UUID.randomUUID()).cookie(new NettyCookie("phylo-token", "test-accessibility")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus(), "Response status is incorrect");

        assertEquals(0, loggingEventListAppender.list.size(), "Logs were entered, but shouldn't have been.");
    }

    @Test
    public void getEnvironmentTypesInternalServerError() {

        when(environmentTypeController.getEnvironmentTypes()).thenThrow(new DataAccessException("Query 123 failed"));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/environment-data-types").cookie(new NettyCookie("phylo-token", "test-environment-type")), String.class
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
    public void environmentTypeControllerHandledExceptionIgnored() {

        when(environmentTypeService.getById(any(UUID.class))).thenReturn(Optional.empty());

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/environment-data-types/" + UUID.randomUUID()).cookie(new NettyCookie("phylo-token", "test-environment-type")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus(), "Response status is incorrect");

        assertEquals(0, loggingEventListAppender.list.size(), "Logs were entered, but shouldn't have been.");
    }

}
