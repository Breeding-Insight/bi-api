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
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitSingleResponse;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.ImportTestUtils;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.parsers.experiment.ExperimentFileColumns;
import org.breedinginsight.services.writers.CSVWriter;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrAPIObservationUnitControllerIntegrationTest extends BrAPITest {

    private Program program;
    private String experimentId;
    private List<String> envIds = new ArrayList<>();
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final List<Column> columns = ExperimentFileColumns.getOrderedColumns();
    private List<Trait> traits;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;
    @Inject
    private DSLContext dsl;
    @Inject
    private UserDAO userDAO;
    @Inject
    private SpeciesDAO speciesDAO;
    @Inject
    private OntologyService ontologyService;
    @Inject
    private BrAPIGermplasmDAO germplasmDAO;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    private final Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
                    (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @BeforeAll
    void setup() throws Exception {
        FannyPack fp = FannyPack.fill("src/test/resources/sql/ImportControllerIntegrationTest.sql");
        ImportTestUtils importTestUtils = new ImportTestUtils();
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        FannyPack brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        // Test User
        User testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).orElseThrow(Exception::new);
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Species
        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));
        SpeciesEntity validSpecies = speciesDAO.findAll().get(0);
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .commonName(validSpecies.getCommonName())
                .id(validSpecies.getId())
                .build();

        // Test Program
        ProgramRequest programRequest = ProgramRequest.builder()
                .name("Test Program")
                .abbreviation("Test")
                .documentationUrl("localhost:8080")
                .objective("To test things")
                .species(speciesRequest)
                .key("TEST")
                .build();
        program = TestUtils.insertAndFetchTestProgram(gson, client, programRequest);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), program.getId());
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Get experiment import map
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=ExperimentsTemplateMap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        String mappingId = JsonParser.parseString(Objects.requireNonNull(response.body())).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        // Add traits to program
        traits = createTraits(2);
        AuthenticatedUser user = new AuthenticatedUser(testUser.getName(), new ArrayList<>(), testUser.getId(), new ArrayList<>());
        try {
            ontologyService.createTraits(program.getId(), traits, user, false);
        } catch (ValidatorException e) {
            System.err.println(e.getErrors());
            throw e;
        }

        // Add germplasm to program
        List<BrAPIGermplasm> germplasm = createGermplasm(1);
        BrAPIExternalReference newReference = new BrAPIExternalReference();
        newReference.setReferenceSource(String.format("%s/programs", BRAPI_REFERENCE_SOURCE));
        newReference.setReferenceID(program.getId().toString());

        germplasm.forEach(germ -> germ.getExternalReferences().add(newReference));

        germplasmDAO.createBrAPIGermplasm(germplasm, program.getId(), null);

        // Make test experiment import
        Map<String, Object> row1 = makeExpImportRow("Env1");
        Map<String, Object> row2 = makeExpImportRow("Env2");

        // Add test observation data
        for (Trait trait : traits) {
            Random random = new Random();

            // TODO: test for sending obs data as double.
            //  A float is returned from the backend instead of double. there is a separate card to fix this.
            // Double val1 = Math.random();

            Float val1 = random.nextFloat();
            row1.put(trait.getObservationVariableName(), val1);
        }

        rows.add(row1);
        rows.add(row2);

        // Import test experiment, environments, and any observations
        JsonObject importResult = importTestUtils.uploadAndFetch(
                writeDataToFile(rows, traits),
                null,
                true,
                client,
                program,
                mappingId);
        experimentId = importResult
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("trial").getAsJsonObject()
                .get("id").getAsString();
        // Add environmentIds.
        envIds.add(getEnvId(importResult, 0));
        envIds.add(getEnvId(importResult, 1));
    }

    @Test
    @SneakyThrows
    public void testPostObsUnitNotFound() {
        BrAPIObservationUnit ou = new BrAPIObservationUnit().observationUnitName("test");

        Flowable<HttpResponse<String>> postCall = client.exchange(
                POST(String.format("/programs/%s/brapi/v2/observationunits",
                        program.getId().toString()), List.of(ou))
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
    public void testPutSingleOUNotFound() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/observationunits?trialDbId=%s", program.getId(), experimentId))
                        .bearerAuth("test-registered-user"),
                String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);

        BrAPIObservationUnit ou = gson.fromJson(responseObj.get("result").getAsJsonObject().get("data").getAsJsonArray().get(0).getAsJsonObject(), BrAPIObservationUnit.class);
        ou.setGermplasmName("updated");

        Flowable<HttpResponse<String>> postCall = client.exchange(
                PUT(String.format("/programs/%s/brapi/v2/observationunits/%s",
                        program.getId().toString(), ou.getObservationUnitDbId()), ou)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> errorResponse = postCall.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @SneakyThrows
    public void testPutMultipleOUsNotFound() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/observationunits?trialDbId=%s", program.getId(), experimentId)).bearerAuth("test-registered-user"),
                String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);

        BrAPIObservationUnit ou = gson.fromJson(responseObj.get("result").getAsJsonObject().get("data").getAsJsonArray().get(0).getAsJsonObject(), BrAPIObservationUnit.class);
        ou.setGermplasmName("updated");

        Flowable<HttpResponse<String>> postCall = client.exchange(
                PUT(String.format("/programs/%s/brapi/v2/observationunits",
                        program.getId().toString()), Map.of(ou.getObservationUnitDbId(), ou))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> errorResponse = postCall.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @SneakyThrows
    public void testGetObsUnitTableNotFound() {
        Flowable<HttpResponse<String>> getCall = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/observationunits/table",
                        program.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = getCall.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void testGetOUListByExpId() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/observationunits?trialDbId=%s", program.getId(), experimentId))
                        .bearerAuth("test-registered-user"),
                String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);

        JsonArray ous = responseObj.getAsJsonObject("result").getAsJsonArray("data");
        assertEquals(2, ous.size());
        List<String> ouNames = new ArrayList<>();
        for(var study : ous) {
            ouNames.add(study.getAsJsonObject().get("observationUnitName").getAsString());
        }
        assertTrue(ouNames.contains("Env1 - a-1"));
        assertTrue(ouNames.contains("Env2 - a-1"));
    }

    @Test
    public void testGetOUById() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/observationunits?trialDbId=%s", program.getId(), experimentId))
                        .bearerAuth("test-registered-user"),
                String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);
        JsonArray ous = responseObj.getAsJsonObject("result").getAsJsonArray("data");
        assertEquals(2, ous.size());
        JsonObject ou = ous.get(0).getAsJsonObject();

        Flowable<HttpResponse<String>> ouCall = client.exchange(
                GET(String.format("/programs/%s/brapi/v2/observationunits/%s", program.getId(), ou.get("observationUnitDbId").getAsString()))
                        .bearerAuth("test-registered-user"),
                String.class
        );

        HttpResponse<String> ouResponse = ouCall.blockingFirst();
        assertEquals(HttpStatus.OK, ouResponse.getStatus());

        AtomicReference<BrAPIObservationUnitSingleResponse> brAPIObservationUnitSingleResponse = new AtomicReference<>();
        Assertions.assertDoesNotThrow(() -> {
            brAPIObservationUnitSingleResponse.set(gson.fromJson(ouResponse.body(), BrAPIObservationUnitSingleResponse.class));
        });

        assertNotNull(brAPIObservationUnitSingleResponse.get());
        assertNotNull(brAPIObservationUnitSingleResponse.get().getResult());
        assertEquals(ou.get("observationUnitDbId").getAsString(), brAPIObservationUnitSingleResponse.get().getResult().getObservationUnitDbId());
    }

    private File writeDataToFile(List<Map<String, Object>> data, List<Trait> traits) throws IOException {
        File file = File.createTempFile("test", ".csv");

        if(traits != null) {
            traits.forEach(trait -> columns.add(
                    Column.builder()
                            .value(trait.getObservationVariableName())
                            .dataType(Column.ColumnDataType.STRING)
                            .build())
            );
        }
        ByteArrayOutputStream byteArrayOutputStream = CSVWriter.writeToCSV(columns, data);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(byteArrayOutputStream.toByteArray());

        return file;
    }

    private String getEnvId(JsonObject result, int index) {
        return result
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(index).getAsJsonObject()
                .get("study").getAsJsonObject()
                .get("brAPIObject").getAsJsonObject()
                .get("externalReferences").getAsJsonArray()
                .get(2).getAsJsonObject()
                .get("referenceID").getAsString();
    }

    private List<BrAPIGermplasm> createGermplasm(int numToCreate) {
        List<BrAPIGermplasm> germplasm = new ArrayList<>();
        for (int i = 0; i < numToCreate; i++) {
            String gid = ""+(i+1);
            BrAPIGermplasm testGermplasm = new BrAPIGermplasm();
            testGermplasm.setGermplasmName(String.format("Germplasm %s [TEST-%s]", gid, gid));
            testGermplasm.setSeedSource("Wild");
            testGermplasm.setAccessionNumber(gid);
            testGermplasm.setDefaultDisplayName(String.format("Germplasm %s", gid));
            JsonObject additionalInfo = new JsonObject();
            additionalInfo.addProperty("importEntryNumber", gid);
            additionalInfo.addProperty("breedingMethod", "Allopolyploid");
            testGermplasm.setAdditionalInfo(additionalInfo);
            List<BrAPIExternalReference> externalRef = new ArrayList<>();
            BrAPIExternalReference testReference = new BrAPIExternalReference();
            testReference.setReferenceSource(BRAPI_REFERENCE_SOURCE);
            testReference.setReferenceID(UUID.randomUUID().toString());
            externalRef.add(testReference);
            testGermplasm.setExternalReferences(externalRef);
            germplasm.add(testGermplasm);
        }

        return germplasm;
    }

    private List<Trait> createTraits(int numToCreate) {
        List<Trait> traits = new ArrayList<>();
        for (int i = 0; i < numToCreate; i++) {
            String varName = "tt_test_" + (i + 1);
            traits.add(Trait.builder()
                    .observationVariableName(varName)
                    .fullName(varName)
                    .entity("Plant " + i)
                    .attribute("height " + i)
                    .traitDescription("test")
                    .programObservationLevel(ProgramObservationLevel.builder().name("Plot").build())
                    .scale(Scale.builder()
                            .scaleName("test scale")
                            .dataType(DataType.NUMERICAL)
                            .validValueMin(0)
                            .validValueMax(100)
                            .build())
                    .method(Method.builder()
                            .description("test method")
                            .methodClass("test method")
                            .build())
                    .build());
        }

        return traits;
    }

    private Map<String, Object> makeExpImportRow(String environment) {
        Map<String, Object> row = new HashMap<>();
        row.put(ExperimentObservation.Columns.GERMPLASM_GID, "1");
        row.put(ExperimentObservation.Columns.TEST_CHECK, "T");
        row.put(ExperimentObservation.Columns.EXP_TITLE, "Test Exp");
        row.put(ExperimentObservation.Columns.EXP_UNIT, "Plot");
        row.put(ExperimentObservation.Columns.EXP_TYPE, "Phenotyping");
        row.put(ExperimentObservation.Columns.ENV, environment);
        row.put(ExperimentObservation.Columns.ENV_LOCATION, "Location A");
        row.put(ExperimentObservation.Columns.ENV_YEAR, "2023");
        row.put(ExperimentObservation.Columns.EXP_UNIT_ID, environment+" - a-1");
        row.put(ExperimentObservation.Columns.REP_NUM, "1");
        row.put(ExperimentObservation.Columns.BLOCK_NUM, "1");
        row.put(ExperimentObservation.Columns.ROW, "1");
        row.put(ExperimentObservation.Columns.COLUMN, "1");
        return row;
    }
}
