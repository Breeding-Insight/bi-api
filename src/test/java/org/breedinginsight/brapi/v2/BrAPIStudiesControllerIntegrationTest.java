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

package org.breedinginsight.brapi.v2;

import com.google.gson.*;
import groovy.lang.Tuple2;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.response.BrAPIStudySingleResponse;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.model.*;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrAPIStudiesControllerIntegrationTest extends BrAPITest {
    private Program program;
    private String experimentId;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @Inject
    private BrAPITestUtils brAPITestUtils;

    private final Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
                    (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @BeforeAll
    void setup() throws Exception {
        // Setup program, experiments, users, species, germplasm, traits.
        Tuple2<Program, List<String>> resultTuple = brAPITestUtils.setupTestProgram(super.getBrapiDsl(), gson);
        // Unpack tuple into the instance variables we need.
        this.program = resultTuple.getV1();
        this.experimentId = resultTuple.getV2().get(0);
    }

    @Test
    public void testGetStudiesAsExperimentalCollaborator() {
        // This test ensures that studies are filtered for experimental collaborators.
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/studies", program.getId()))
                        .bearerAuth("other-registered-user"),
                String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);
        JsonArray studies = responseObj.getAsJsonObject("result").getAsJsonArray("data");
        assertEquals(2, studies.size());
        List<String> studyNames = new ArrayList<>();
        for(var study : studies) {
            studyNames.add(study.getAsJsonObject().get("studyName").getAsString());
        }
        assertTrue(studyNames.contains("Env3"));
        assertTrue(studyNames.contains("Env4"));
    }

    @Test
    @SneakyThrows
    public void testPostGetStudiesNotFound() {
        BrAPIStudy study = new BrAPIStudy().studyName("test study")
                .studyCode("123")
                .studyType("Phenotyping Trial")
                .studyDescription("Test study description")
                .active(true)
                .startDate(OffsetDateTime.of(2021, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC))
                .endDate(OffsetDateTime.of(2021, 2, 5, 0, 0, 0, 0, ZoneOffset.UTC));

        Flowable<HttpResponse<String>> postCall = client.exchange(
                POST(String.format("/programs/%s/brapi/v2/studies",
                        program.getId().toString()), List.of(study))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = postCall.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @SneakyThrows
    public void testPutGetStudiesNotFound() {
        BrAPIStudy study = new BrAPIStudy().studyName("test study")
                .studyCode("123")
                .studyType("Phenotyping Trial")
                .studyDescription("Test study description")
                .active(true)
                .startDate(OffsetDateTime.of(2021, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC))
                .endDate(OffsetDateTime.of(2021, 2, 5, 0, 0, 0, 0, ZoneOffset.UTC));

        Flowable<HttpResponse<String>> putCall = client.exchange(
                PUT(String.format("/programs/%s/brapi/v2/studies/abc",
                        program.getId().toString()), List.of(study))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = putCall.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void testGetStudiesListByExpId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/studies?trialDbId=%s", program.getId(), experimentId))
                        .bearerAuth("test-registered-user"),
                String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);
        JsonArray studies = responseObj.getAsJsonObject("result").getAsJsonArray("data");
        assertEquals(2, studies.size());
        List<String> studyNames = new ArrayList<>();
        for(var study : studies) {
            studyNames.add(study.getAsJsonObject().get("studyName").getAsString());
        }
        assertTrue(studyNames.contains("Env1"));
        assertTrue(studyNames.contains("Env2"));
    }

    @Test
    public void testGetStudyById() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/studies?trialDbId=%s", program.getId(), experimentId))
                        .bearerAuth("test-registered-user"),
                String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);
        JsonArray studies = responseObj.getAsJsonObject("result").getAsJsonArray("data");
        assertEquals(2, studies.size());
        JsonObject study = studies.get(0).getAsJsonObject();

        Flowable<HttpResponse<String>> studyCall = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/studies/%s", program.getId(), study.get("studyDbId").getAsString()))
                        .bearerAuth("test-registered-user"),
                String.class
        );

        HttpResponse<String> studyResponse = studyCall.blockingFirst();
        assertEquals(HttpStatus.OK, studyResponse.getStatus());

        AtomicReference<BrAPIStudySingleResponse> brAPIStudySingleResponse = new AtomicReference<>();
        Assertions.assertDoesNotThrow(() -> {
            brAPIStudySingleResponse.set(gson.fromJson(studyResponse.body(), BrAPIStudySingleResponse.class));
        });

        assertNotNull(brAPIStudySingleResponse.get());
        assertNotNull(brAPIStudySingleResponse.get().getResult());
        assertEquals(study.get("studyDbId").getAsString(), brAPIStudySingleResponse.get().getResult().getStudyDbId());
    }

}
