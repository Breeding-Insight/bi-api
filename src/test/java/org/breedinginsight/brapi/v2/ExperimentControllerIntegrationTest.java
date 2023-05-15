package org.breedinginsight.brapi.v2;

import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SharedOntologyProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.io.File;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExperimentControllerIntegrationTest extends BrAPITest {

    private FannyPack securityFp;
    private FannyPack brapiFp;
    private FannyPack brapiObservationFp;
    private Program program;
    private String germplasmImportId;
    private final String GERMPLASM_LIST_NAME = "Program Germplasm List";
    private final String GERMPLASM_LIST_DESC = "Program Germplasm List";
    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDAO programDAO;
    @Inject
    private UserDAO userDAO;
    @Inject
    private SpeciesDAO speciesDAO;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @BeforeAll
    void setup() throws Exception {
        securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        //brapiObservationFp = FannyPack.fill("src/test/resources/sql/brapi/BrAPIOntologyControllerIntegrationTest.sql");

        // Test User
        User testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
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
                .species(speciesRequest)
                .key("TEST")
                .build();
        program = TestUtils.insertAndFetchTestProgram(gson, client, programRequest);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), program.getId().toString());

        // Add trait to program
        addTrait(program);

        // Add germplasm to program
        addGermplasm(program);

        // Add experiemnts to program
        addExperiments(program);
    }

    private void addExperiments(Program program) throws InterruptedException {
        // Get experiment system import
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=experimenttemplatemap").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        germplasmImportId = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        // Insert program germplasm
        File file = new File("src/test/resources/files/experiment_controller/germplasm_import.csv");
        Map<String, String> germplasmListInfo = Map.ofEntries(
                Map.entry("germplasmListName", GERMPLASM_LIST_NAME),
                Map.entry("germplasmListDescription", GERMPLASM_LIST_DESC)
        );
        TestUtils.uploadDataFile(client, program.getId(), germplasmImportId, germplasmListInfo, file);
    }

    private void addGermplasm(Program program) throws InterruptedException {
        // Get germplasm system import
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=germplasmtemplatemap").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        germplasmImportId = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        // Insert program germplasm
        File file = new File("src/test/resources/files/experiment_controller/germplasm_import.csv");
        Map<String, String> germplasmListInfo = Map.ofEntries(
                Map.entry("germplasmListName", GERMPLASM_LIST_NAME),
                Map.entry("germplasmListDescription", GERMPLASM_LIST_DESC)
        );
        TestUtils.uploadDataFile(client, program.getId(), germplasmImportId, germplasmListInfo, file);
    }
    private void addTrait(Program program) {

        // Add a trait
        Trait trait = new Trait();
        trait.setTraitDescription("trait 1 description");
        trait.setEntity("entity1");
        trait.setObservationVariableName("ObsVar1");
        trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.TEXT);
        Method method1 = new Method();
        trait.setScale(scale1);
        trait.setMethod(method1);
        trait.setTraitClass("Pheno trait");
        trait.setAttribute("leaf length");
        trait.setMainAbbreviation("abbrev1");
        trait.setSynonyms(List.of("test1", "test2"));
        trait.getMethod().setMethodClass("Estimation");
        trait.getMethod().setDescription("A method");

        List<Trait> traits = List.of(trait);

        // Call endpoint
        TestUtils.insertTestTraits(gson, client, program, traits);
    }

    @Test
    @Order(1)
    void getAllProgramsNoSharedPrograms() {
        String url = String.format("/programs/%s/ontology/shared/programs", mainProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(url).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(3, data.size(), "Wrong number of programs returned");

        // Check all are not shared and are inactive
        for (JsonElement element: data) {
            JsonObject program = element.getAsJsonObject();

            assertFalse(program.get("shared").getAsBoolean(), "Shared should have been false");
            assertNull(program.get("accepted"), "Accepted should have been false");
            assertNull(program.get("editable"), "Editable should have been false");
        }
    }

    @Test
    @Order(2)
    void addSharedPrograms() {

        String url = String.format("/programs/%s/ontology/shared/programs", mainProgram.getId());
        List<SharedOntologyProgramRequest> requests = new ArrayList<>();
        requests.add(new SharedOntologyProgramRequest(otherProgram.getId(), otherProgram.getName()));
        requests.add(new SharedOntologyProgramRequest(thirdProgram.getId(), thirdProgram.getName()));
        String json = gson.toJson(requests);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(url, json)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(2, data.size(), "Wrong number of programs returned");

        // Check all are not shared and are inactive
        for (JsonElement element: data) {
            JsonObject program = element.getAsJsonObject();

            assertTrue(program.get("shared").getAsBoolean(), "Shared should have been true");
            assertFalse(program.get("accepted").getAsBoolean(), "Accepted should have been false");
            assertTrue(program.get("editable").getAsBoolean(), "Editable should have been true");
        }
    }

    @Test
    @Order(3)
    void subscribeOntologyProgramHasTraitsError() {
        String url = String.format("/programs/%s/ontology/subscribe/%s", thirdProgram.getId(), mainProgram.getId());

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT(url, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(3)
    void subscribeOntologySuccess() {
        String url = String.format("/programs/%s/ontology/subscribe/%s", otherProgram.getId(), mainProgram.getId());

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT(url, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @Order(4)
    void getSubscribedOntologyOptions() {

        String url = String.format("/programs/%s/ontology/subscribe", otherProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(url).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(1, data.size(), "Wrong number of programs returned");

        // Check all are not shared and are inactive
        for (JsonElement element: data) {
            JsonObject program = element.getAsJsonObject();

            assertTrue(program.get("subscribed").getAsBoolean());
            assertTrue(program.get("editable").getAsBoolean());
        }
    }

    @Test
    @Order(4)
    void getAllProgramsSharedPrograms() {
        String url = String.format("/programs/%s/ontology/shared/programs", mainProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(url).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(3, data.size(), "Wrong number of programs returned");

        // Check all are not shared and are inactive
        for (JsonElement element: data) {
            JsonObject program = element.getAsJsonObject();

            if (program.get("programId").getAsString().equals(fourthProgram.getId().toString())) {
                assertFalse(program.get("shared").getAsBoolean());
            } else {
                assertTrue(program.get("shared").getAsBoolean(), "Shared should have been true");
                assertTrue(program.get("editable").getAsBoolean(), "Editable should have been false");
                if (program.get("programId").getAsString().equals(otherProgram.getId().toString())) {
                    assertTrue(program.get("accepted").getAsBoolean());
                } else {
                    assertFalse(program.get("accepted").getAsBoolean());
                }
            }
        }
    }

    @Test
    @Order(4)
    void getOnlySharedPrograms() {
        String url = String.format("/programs/%s/ontology/shared/programs?shared=true", mainProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(url).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(2, data.size(), "Wrong number of programs returned");

        // Check all are not shared and are inactive
        for (JsonElement element: data) {
            JsonObject program = element.getAsJsonObject();

            assertTrue(program.get("shared").getAsBoolean(), "Shared should have been true");
        }
    }

    @Test
    @Order(4)
    void shareOntologySubscribedToOtherProgram() {
        // Cannot share ontology if you are using a shared ontology
        String url = String.format("/programs/%s/ontology/shared/programs", otherProgram.getId());
        List<SharedOntologyProgramRequest> requests = new ArrayList<>();
        requests.add(new SharedOntologyProgramRequest(fourthProgram.getId(), fourthProgram.getName()));
        String json = gson.toJson(requests);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(url, json)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    @Order(4)
    void revokeOntologyUneditable() {
        // Ontology cannot be revoke if shared program has accepted and has observations
        super.getBrapiDsl().execute(brapiObservationFp.get("AddObservations"), otherProgram.getId().toString());

        String url = String.format("/programs/%s/ontology/shared/programs/%s", mainProgram.getId(), otherProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE(url).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        super.getBrapiDsl().execute(brapiObservationFp.get("DeleteObservations"));
    }

    @Test
    @Order(4)
    void unsubscribeOntologyUneditableError() {
        super.getBrapiDsl().execute(brapiObservationFp.get("AddObservations"), otherProgram.getId().toString());

        String url = String.format("/programs/%s/ontology/subscribe/%s", otherProgram.getId(), mainProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE(url, "").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        super.getBrapiDsl().execute(brapiObservationFp.get("DeleteObservations"));
    }

    @Test
    @Order(5)
    void unsubscribeOntologySuccess() {

        String url = String.format("/programs/%s/ontology/subscribe/%s", otherProgram.getId(), mainProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE(url, "").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    @Order(5)
    void revokeOntologySuccess() {

        String url = String.format("/programs/%s/ontology/shared/programs/%s", mainProgram.getId(), thirdProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE(url).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    void shareSelfError() {
        // Test that program cannot share with themselves

        String url = String.format("/programs/%s/ontology/shared/programs", mainProgram.getId());
        List<SharedOntologyProgramRequest> requests = new ArrayList<>();
        requests.add(new SharedOntologyProgramRequest(mainProgram.getId(), mainProgram.getName()));
        String json = gson.toJson(requests);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(url, json)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    void revokeOntologyProgramNotExist() {
        String url = String.format("/programs/%s/ontology/shared/programs/%s", UUID.randomUUID(), otherProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE(url).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void revokeOntologySharedProgramNotExist() {
        String url = String.format("/programs/%s/ontology/shared/programs/%s", mainProgram.getId(), UUID.randomUUID());
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE(url).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void revokeOntologySharedProgramNotShared() {
        String url = String.format("/programs/%s/ontology/shared/programs/%s", mainProgram.getId(), fourthProgram.getId());
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE(url).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void shareOntologyProgramNotExist() {

        String url = String.format("/programs/%s/ontology/shared/programs", mainProgram.getId());
        List<SharedOntologyProgramRequest> requests = new ArrayList<>();
        requests.add(new SharedOntologyProgramRequest(UUID.randomUUID(), "I don't exist"));
        String json = gson.toJson(requests);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(url, json)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    void shareOntologySharedProgramNotExist() {

        String url = String.format("/programs/%s/ontology/shared/programs", UUID.randomUUID());
        List<SharedOntologyProgramRequest> requests = new ArrayList<>();
        requests.add(new SharedOntologyProgramRequest(otherProgram.getId(), otherProgram.getName()));
        String json = gson.toJson(requests);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(url, json)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }
}
