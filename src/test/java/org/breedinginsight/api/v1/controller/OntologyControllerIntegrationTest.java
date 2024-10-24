package org.breedinginsight.api.v1.controller;

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
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OntologyControllerIntegrationTest extends BrAPITest {

    private FannyPack securityFp;
    private FannyPack brapiFp;
    private FannyPack brapiObservationFp;
    private Program mainProgram;
    private Program otherProgram;
    private Program thirdProgram;
    private Program fourthProgram;

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
        // Create two programs with fanny pack
        securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        brapiObservationFp = FannyPack.fill("src/test/resources/sql/brapi/BrAPIOntologyControllerIntegrationTest.sql");

        User testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        SpeciesEntity validSpecies = speciesDAO.findAll().get(0);
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .commonName(validSpecies.getCommonName())
                .id(validSpecies.getId())
                .build();
        ProgramRequest programRequest1 = ProgramRequest.builder()
                .name("Test Program1")
                .abbreviation("test1")
                .documentationUrl("localhost:8080")
                .objective("To test things")
                .species(speciesRequest)
                .key("OTA")
                .build();
        ProgramRequest programRequest2 = ProgramRequest.builder()
                .name("Test Program2")
                .abbreviation("test2")
                .documentationUrl("localhost:8080")
                .objective("To test things")
                .species(speciesRequest)
                .key("OTB")
                .build();
        ProgramRequest programRequest3 = ProgramRequest.builder()
                .name("Test Program3")
                .abbreviation("test3")
                .documentationUrl("localhost:8080")
                .objective("To test things")
                .species(speciesRequest)
                .key("OTC")
                .build();
        ProgramRequest programRequest4 = ProgramRequest.builder()
                .name("Test Program4")
                .abbreviation("test3")
                .documentationUrl("localhost:8080")
                .objective("To test things")
                .species(speciesRequest)
                .key("OTD")
                .build();


        TestUtils.insertAndFetchTestProgram(gson, client, programRequest1);
        TestUtils.insertAndFetchTestProgram(gson, client, programRequest2);
        TestUtils.insertAndFetchTestProgram(gson, client, programRequest3);
        TestUtils.insertAndFetchTestProgram(gson, client, programRequest4);

        // Get main program
        List<Program> programs = programDAO.getAll();
        mainProgram = programs.get(0);
        otherProgram = programs.get(1);
        thirdProgram = programs.get(2);
        fourthProgram = programs.get(3);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), mainProgram.getId().toString());
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), otherProgram.getId().toString());
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), thirdProgram.getId().toString());

        // Add trait to program
        addTrait(mainProgram);
        addTrait(thirdProgram);
    }

    private void addTrait(Program program) {

        // Add a trait
        Trait trait = new Trait();
        trait.setTraitDescription("trait 1 description");
        trait.setEntity("entity1");
        trait.setObservationVariableName("Test Trait");
        trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
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
