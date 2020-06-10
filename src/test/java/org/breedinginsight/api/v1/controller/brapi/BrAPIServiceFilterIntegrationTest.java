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

package org.breedinginsight.api.v1.controller.brapi;

import io.kowalski.fannypack.FannyPack;
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
import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
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

import javax.inject.Inject;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrAPIServiceFilterIntegrationTest {

    private ProgramEntity validProgram;
    private TraitEntity validVariable;

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
    @SneakyThrows
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
        CompletableFuture<Boolean> urlCheckFuture = checkBrAPIClientExecution(phenoUrl);

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
        assertTrue(urlCheckFuture.get(), "Url was not as expected");
    }

    @Test
    @Order(2)
    @SneakyThrows
    public void urlChangesForDifferentRequestsCallTwo() {

        String coreUrl1 = "http://core-test" + UUID.randomUUID().toString();
        String phenoUrl1 = "http://pheno-test" + UUID.randomUUID().toString();
        String genoUrl1 = "http://geno-test" + UUID.randomUUID().toString();
        ProgramBrAPIEndpoints programBrAPIEndpoints = getBrAPIEndpoints(coreUrl1, phenoUrl1, genoUrl1);

        reset(programService);
        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(programBrAPIEndpoints);
        when(programService.exists(any(UUID.class))).thenReturn(true);

        // Assert our brapi url was used
        CompletableFuture<Boolean> urlCheckFuture = checkBrAPIClientExecution(phenoUrl1);

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
        assertTrue(urlCheckFuture.get(), "Url was not as expected");
    }

    @Test
    @SneakyThrows
    public void getTraitsUsesFilter() {

        String phenoUrl = "http://getTraits" + UUID.randomUUID().toString();
        ProgramBrAPIEndpoints programBrAPIEndpoints = getBrAPIEndpoints("", phenoUrl, "");

        reset(programService);
        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(programBrAPIEndpoints);
        when(programService.exists(any(UUID.class))).thenReturn(true);

        CompletableFuture<Boolean> urlCheckFuture = checkBrAPIClientExecution(phenoUrl);

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

        assertTrue(urlCheckFuture.get(), "Url was not as expected");
    }

    @Test
    @SneakyThrows
    public void getTraitSingleUsesFilter() {

        String phenoUrl = "http://getTraitSingle" + UUID.randomUUID().toString();
        ProgramBrAPIEndpoints programBrAPIEndpoints = getBrAPIEndpoints(null, phenoUrl, null);

        reset(programService);
        when(programService.getBrapiEndpoints(any(UUID.class))).thenReturn(programBrAPIEndpoints);
        when(programService.exists(any(UUID.class))).thenReturn(true);

        CompletableFuture<Boolean> urlCheckFuture = checkBrAPIClientExecution(phenoUrl);

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

        assertTrue(urlCheckFuture.get(), "Url was not as expected");
    }

    public ProgramBrAPIEndpoints getBrAPIEndpoints(String coreUrl, String phenoUrl, String genoUrl){
        return ProgramBrAPIEndpoints.builder()
                .coreUrl(coreUrl == null ? Optional.empty() : Optional.of(coreUrl))
                .genoUrl(genoUrl == null ? Optional.empty() : Optional.of(genoUrl))
                .phenoUrl(phenoUrl == null ? Optional.empty() : Optional.of(phenoUrl))
                .build();
    }

    public CompletableFuture<Boolean> checkBrAPIClientExecution(String expectedUrl) throws AssertionFailedError {

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Takes advantage of brapi library code to mimic no return results from api.
        Answer<Optional<Object>> checkBrAPIExecution = new Answer<Optional<Object>>() {
            @Override
            public Optional<Object> answer(InvocationOnMock invocation) throws AssertionFailedError {
                BrAPIClient executingBrAPIClient = (BrAPIClient) invocation.getMock();
                // Check that our url is correct
                future.complete(expectedUrl.equals(executingBrAPIClient.brapiURI()));
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

        return future;

    }

}
