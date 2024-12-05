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
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.brapps.importer.ImportTestUtils;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.SpeciesService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.*;

import static io.micronaut.http.HttpRequest.DELETE;
import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ListControllerIntegrationTest extends BrAPITest {

    private Program program;
    private String germplasmImportId;
    private ImportTestUtils importTestUtils;
    private String mappingId;
    private BiUserEntity testUser;


    @Inject
    private SpeciesService speciesService;
    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;
    @Inject
    private UserDAO userDAO;
    @Inject
    private DSLContext dsl;

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
                    (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    private final String germplasmListName = "Program List";
    private final String germplasmListDesc = "Program List";

    @Inject
    private OntologyService ontologyService;

    private String newExperimentWorkflowId;

    @BeforeAll
    @SneakyThrows
    public void setup() {
        importTestUtils = new ImportTestUtils();
        newExperimentWorkflowId = importTestUtils.getExperimentWorkflowId(client, 0);
        Map<String, Object> setupObjects = importTestUtils.setup(client, gson, dsl, speciesService, userDAO, super.getBrapiDsl(), "ExperimentsTemplateMap");
        mappingId = (String) setupObjects.get("mappingId");
        program = (Program) setupObjects.get("program");
        testUser = (BiUserEntity) setupObjects.get("testUser");
        FannyPack securityFp = (FannyPack) setupObjects.get("securityFp");

        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Generate traits and persist them.
        List<Trait> traits = importTestUtils.createTraits(1);
        AuthenticatedUser user = new AuthenticatedUser(testUser.getName(), new ArrayList<>(), testUser.getId(), new ArrayList<>());
        try {
            ontologyService.createTraits(program.getId(), traits, user, false);
        } catch (ValidatorException e) {
            System.err.println(e.getErrors());
            throw e;
        }

        // Get germplasm system import.
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=germplasmtemplatemap").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        germplasmImportId = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        // Insert program germplasm.
        File file = new File("src/test/resources/files/germplasm_import/minimal_germplasm_import.csv");
        Map<String, String> germplasmListInfo = Map.ofEntries(
                Map.entry("germplasmListName", germplasmListName),
                Map.entry("germplasmListDescription", germplasmListDesc)
        );
        TestUtils.uploadDataFile(client, program.getId(), germplasmImportId, germplasmListInfo, file);

        // Make test experiment import.
        Map<String, Object> newExp = new HashMap<>();
        newExp.put(ExperimentObservation.Columns.GERMPLASM_GID, "1");
        newExp.put(ExperimentObservation.Columns.TEST_CHECK, "T");
        newExp.put(ExperimentObservation.Columns.EXP_TITLE, "Test Exp");
        newExp.put(ExperimentObservation.Columns.EXP_UNIT, "Plot");
        newExp.put(ExperimentObservation.Columns.EXP_TYPE, "Phenotyping");
        newExp.put(ExperimentObservation.Columns.ENV, "New Env");
        newExp.put(ExperimentObservation.Columns.ENV_LOCATION, "Location A");
        newExp.put(ExperimentObservation.Columns.ENV_YEAR, "2023");
        newExp.put(ExperimentObservation.Columns.EXP_UNIT_ID, "a-1");
        newExp.put(ExperimentObservation.Columns.REP_NUM, "1");
        newExp.put(ExperimentObservation.Columns.BLOCK_NUM, "1");
        newExp.put(ExperimentObservation.Columns.ROW, "1");
        newExp.put(ExperimentObservation.Columns.COLUMN, "1");
        newExp.put(traits.get(0).getObservationVariableName(), "1");

        JsonObject result = importTestUtils.uploadAndFetchWorkflow(
                importTestUtils.writeExperimentDataToFile(List.of(newExp), traits), null, true, client, program, mappingId, newExperimentWorkflowId);
    }

    @Test
    @SneakyThrows
    public void getAllListsSuccess() {
        // A GET request to the brapi/v2/lists endpoint with no query params should return all lists.
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/lists", program.getId().toString()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        // Ensure 200 OK response.
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Parse result.
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        // Ensure all lists are returned.
        assertEquals(2, data.size(), "Wrong number of lists were returned");

        // Ensure the list names and types are as expected.
        List<String> listNames = List.of(germplasmListName, "Observation Dataset");
        List<String> listTypes = List.of("germplasm", "observationVariables");
        for (JsonElement element: data) {
            JsonObject list = element.getAsJsonObject();
            if (listNames.contains(list.get("listName").getAsString())) {
                int index = listNames.indexOf(list.get("listName").getAsString());
                assertEquals(list.get("listName").getAsString(), listNames.get(index), "List name incorrect");
            } else {
                throw new AssertionFailedError("List name not found");
            }
            if (listTypes.contains(list.get("listType").getAsString())) {
                int index = listTypes.indexOf(list.get("listType").getAsString());
                assertEquals(list.get("listType").getAsString(), listTypes.get(index), "List type incorrect");
            } else {
                throw new AssertionFailedError("List type not found");
            }
        }
    }


    @Test
    @SneakyThrows
    public void deleteListSuccess() {
        // A GET request to the brapi/v2/lists endpoint with no query params should return all lists.
        Flowable<HttpResponse<String>> getCall = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/lists", program.getId().toString()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        // Ensure 200 OK response for fetching lists.
        HttpResponse<String> beforeResponse = getCall.blockingFirst();
        assertEquals(HttpStatus.OK, beforeResponse.getStatus());

        // Parse result.
        JsonObject beforeResult = JsonParser.parseString(beforeResponse.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray beforeData = beforeResult.getAsJsonArray("data");

        // A DELETE request to the brapi/v2/lists/<listDbId> endpoint with no query params should delete the list.
        String deleteListDbId = beforeData.get(0).getAsJsonObject().get("listDbId").getAsString();
        Flowable<HttpResponse<String>> deleteCall = client.exchange(
                DELETE(String.format("/programs/%s/brapi/v2/lists/%s", program.getId().toString(), deleteListDbId))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        // Ensure 204 NO_CONTENT response for deleting a list.
        HttpResponse<String> deleteResponse = deleteCall.blockingFirst();
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatus());

    }
}
