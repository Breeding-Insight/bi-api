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
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.*;
import org.breedinginsight.utilities.Utilities;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.*;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.*;


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrAPITrialsControllerIntegrationTest extends BrAPITest {
    private Program program;
    private String experiment1Id;
    private String experiment2Id;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

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
        this.experiment1Id = resultTuple.getV2().get(0);
        this.experiment2Id = resultTuple.getV2().get(1);
    }

    /**
    * Tests that all trials in a program are returned for a system admin user.
    */
    @Test
    public void testGetTrials() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/trials", program.getId()))
                        .bearerAuth("test-registered-user"),
                String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);
        JsonArray trialsJson = responseObj.getAsJsonObject("result").getAsJsonArray("data");
        assertEquals(2, trialsJson.size());

        List<BrAPITrial> trials = new ArrayList<>();
        trials.add(gson.fromJson(trialsJson.get(0).getAsJsonObject(), BrAPITrial.class));
        trials.add(gson.fromJson(trialsJson.get(1).getAsJsonObject(), BrAPITrial.class));
        String source = Utilities.generateReferenceSource(BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS);

        // Check names and experimentIds (the BI-assigned UUID stored as an xref).
        List<String> expNames = new ArrayList<>();
        List<String> expIds = new ArrayList<>();
        for (BrAPITrial trial : trials) {
            String experimentId = Utilities.getExternalReference(trial.getExternalReferences(), source).get().getReferenceId();
            expIds.add(experimentId);
            expNames.add(trial.getTrialName());
        }
        assert(expNames.contains("xyz"));
        assert(expNames.contains("Test Exp"));
        assert(expIds.contains(experiment1Id));
        assert(expIds.contains(experiment2Id));
    }

    /**
    * Tests that trials are filtered for an experimental collaborator.
    */
    @Test
    public void testGetTrialsAsExperimentalCollaborator() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/trials", program.getId()))
                        .bearerAuth("other-registered-user"),
                String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);
        JsonArray trials = responseObj.getAsJsonObject("result").getAsJsonArray("data");
        assertEquals(1, trials.size());
        BrAPITrial trial = gson.fromJson(trials.get(0).getAsJsonObject(), BrAPITrial.class);
        assertEquals("xyz", trial.getTrialName());
        String source = Utilities.generateReferenceSource(BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS);
        String experimentId = Utilities.getExternalReference(trial.getExternalReferences(), source).get().getReferenceId();
        assertEquals(this.experiment2Id, experimentId);
    }

}
