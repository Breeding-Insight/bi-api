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
import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams;
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.brapi.v2.model.pheno.response.BrAPIObservationLevelListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.daos.TraitDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.SpeciesService;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.*;

import static io.micronaut.http.HttpRequest.*;
import static org.breedinginsight.TestUtils.insertAndFetchTestProgram;
import static org.junit.jupiter.api.Assertions.*;


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TraitControllerIntegrationTest extends BrAPITest {

    private TraitEntity validTrait;
    private FannyPack fp;
    private FannyPack brapiFp;

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private TraitDao traitDao;
    @Inject
    private TraitService traitService;
    @Inject
    private UserDAO userDAO;
    @Inject
    private SpeciesService speciesService;

    private Species validSpecies;
    private List<Trait> validTraits;
    private Program validProgram;
    private Program otherValidProgram;
    private ProgramEntity missingBrapiProgram;
    private String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @BeforeAll
    @SneakyThrows
    public void setup() {

        brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));

        // Insert our traits into the db
        fp = FannyPack.fill("src/test/resources/sql/TraitControllerIntegrationTest.sql");
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");

        // Insert system roles
        User testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Insert program
        dsl.execute(fp.get("InsertProgramNotBrapi"));
        missingBrapiProgram = programDao.findAll().get(0);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), missingBrapiProgram.getId().toString());

        validSpecies = getTestSpecies();

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .commonName(validSpecies.getCommonName())
                .id(validSpecies.getId())
                .build();

        ProgramRequest program = ProgramRequest.builder()
                .name("Test Program")
                .abbreviation("test")
                .documentationUrl("localhost:8080")
                .objective("To test things")
                .species(speciesRequest)
                .key("TEST")
                .build();

        validProgram = insertAndFetchTestProgram(gson, client, program);

        // Insert program observation level
        dsl.execute(fp.get("InsertProgramObservationLevel"));

        // Insert program ontology sql
        //dsl.execute(fp.get("InsertProgramOntology"));

        // Insert Trait
        dsl.execute(fp.get("InsertMethod"));
        dsl.execute(fp.get("InsertScale"));
        dsl.execute(fp.get("InsertTrait"));

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), validProgram.getId().toString());

        // Retrieve our valid trait
        validTrait = traitDao.findAll().get(0);

        // Insert other program
        //dsl.execute(fp.get("InsertOtherProgram"));

        ProgramRequest otherProgram = ProgramRequest.builder()
                .name("Other Test Program")
                .abbreviation("othertest")
                .documentationUrl("localhost:8080")
                .objective("To test other things")
                .species(speciesRequest)
                .key("TESTO")
                .build();

        otherValidProgram = insertAndFetchTestProgram(gson, client, otherProgram);

        dsl.execute(fp.get("InsertOtherProgramObservationLevel"));
        //dsl.execute(fp.get("InsertOtherProgramOntology"));



        //otherValidProgram = programDao.fetchByName("Other Test Program").get(0);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), otherValidProgram.getId().toString());
    }

    public Species getTestSpecies() {
        List<Species> species = speciesService.getAll();
        return species.get(0);
    }

    @Test
    @SneakyThrows
    @Order(1)
    public void getTraitsNoExistInBrAPI() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=true").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @Test
    @SneakyThrows
    @Order(1)
    public void getTraitTagsDefaultFavorites() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + otherValidProgram.getId() + "/traits/tags").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        assertEquals(1, data.size(), "Wrong number of results returned.");
        assertEquals("favorites", data.get(0).getAsString(), "Wrong default tag returned");
    }

    @Test
    @SneakyThrows
    @Order(2)
    public void getTraitSingleNoExistInBrAPI() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTrait.getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    }

    //region POST traits
    @Test
    @SneakyThrows
    @Order(2)
    public void createTraitsBrAPIError() {

        // Turn off brapi container to fake error
        Trait trait = new Trait();
        trait.setObservationVariableName("Test Trait3");
        trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale = new Scale();
        scale.setScaleName("Test Scale");
        scale.setDataType(DataType.TEXT);
        Method method = new Method();
        trait.setScale(scale);
        trait.setMethod(method);

        setBrAPIProperties(trait);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + missingBrapiProgram.getId() + "/traits", List.of(trait))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());

    }

    @Test
    @Order(3)
    public void postTraitsMultiple() {

        // Add species to BrAPI server
        //var brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        //super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));

        dsl.execute(fp.get("DeleteTrait"));

        Trait trait1 = new Trait();
        trait1.setTraitDescription("trait 1 description");
        trait1.setEntity("entity1");
        trait1.setObservationVariableName("Test Trait");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.TEXT);
        Method method1 = new Method();
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        Trait trait2 = new Trait();
        trait2.setTraitDescription("trait 2 description");
        trait2.setEntity("entity2");
        trait2.setObservationVariableName("Test Trait1");
        trait2.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale2 = new Scale();
        scale2.setScaleName("Test Scale1");
        scale2.setDataType(DataType.TEXT);
        Method method2 = new Method();
        trait2.setScale(scale2);
        trait2.setMethod(method2);

        // Set the brapi properties
        setBrAPIProperties(trait1);
        setBrAPIProperties(trait2);

        // Set the tags
        trait1.setTags(List.of("leaf trait"));
        trait2.setTags(List.of("stem trait"));

        List<Trait> traits = List.of(trait1, trait2);

        // Call endpoint
        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", traits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");

        Boolean trait1Found = false;
        Boolean trait2Found = false;
        for (JsonElement traitJson: data) {
            JsonObject trait = (JsonObject) traitJson;
            String traitName = trait.get("observationVariableName").getAsString();
            if (traitName.equals(trait1.getObservationVariableName())){
                trait1Found = true;
                checkTraitFullResponse(trait, trait1);
                trait1.setId(UUID.fromString(trait.get("id").getAsString()));
            } else if (traitName.equals(trait2.getObservationVariableName())) {
                trait2Found = true;
                checkTraitFullResponse(trait, trait2);
                trait2.setId(UUID.fromString(trait.get("id").getAsString()));
            }
        }

        if (!trait1Found || !trait2Found){
            throw new AssertionFailedError("Both traits were not returned");
        }

        validTraits = traits;
    }

    @Test
    @SneakyThrows
    @Order(4)
    public void getTraitTags() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/tags").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        assertEquals(3, data.size(), "Wrong number of results returned.");
        Set<String> tagsList = new HashSet<>(List.of("favorites", "leaf trait", "stem trait"));
        Set<String> foundTags = new HashSet<>();
        for (JsonElement tagElement: data) {
            foundTags.add(tagElement.getAsString());
        }

        assertTrue(tagsList.equals(foundTags), "Returned tags do not equal expected tags");
    }

    @Test
    @Order(4)
    public void postTraitsMultipleExistInDb() {

        // Call endpoint
        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", validTraits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        // Check return
        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(2, rowErrors.size(), "Wrong number of row errors returned");
        JsonObject rowError = rowErrors.get(0).getAsJsonObject();

        // Returns an error for duplicate names in db
        JsonArray errors = rowError.getAsJsonArray("errors");
        assertEquals(1, errors.size(), "Wrong number of errors returned");
        JsonObject duplicateError = errors.get(0).getAsJsonObject();
        assertEquals(409, duplicateError.get("httpStatusCode").getAsInt(), "Wrong error code returned");
    }

    @Test
    @Order(4)
    public void postTraitsNotSharedBetweenPrograms() {

        // No traits should be ignored because duplicate traits are not shared between programs

        // Call endpoint
        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + otherValidProgram.getId() + "/traits", validTraits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");

        assertEquals(validTraits.size(), data.size(), "Traits were ignored, but should not be");

    }

    @Test
    @Order(4)
    public void createTraitsDuplicateInRequest() {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", List.of(validTraits.get(0), validTraits.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        // Check for conflict response
        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(2, rowErrors.size(), "Wrong number of row errors returned");
        JsonObject rowError = rowErrors.get(0).getAsJsonObject();

        // Returns an error for duplicate names in file, duplicate abbreviations in file, duplicate names in db, duplicate abbreviations in db
        JsonArray errors = rowError.getAsJsonArray("errors");
        assertEquals(4, errors.size(), "Wrong number of errors returned");
        JsonObject duplicateError = errors.get(0).getAsJsonObject();
        assertEquals(409, duplicateError.get("httpStatusCode").getAsInt(), "Wrong error code returned");
    }

    @Test
    @Order(4)
    public void createTraitsLevelDoesNotExist() {
        Trait trait1 = new Trait();
        trait1.setTraitDescription("trait 1 description");
        trait1.setEntity("entity1");
        trait1.setObservationVariableName("Unique Trait");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Not Exist").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.TEXT);
        Method method1 = new Method();
        trait1.setScale(scale1);
        trait1.setMethod(method1);
        setBrAPIProperties(trait1);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", List.of(trait1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(1, data.size(), "Wrong number of traits");
    }

    @Test
    @Order(4)
    public void createTraitsValidationError() {

        Trait trait1 = new Trait();
        trait1.setObservationVariableName("Test Trait3");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.TEXT);
        Method method1 = new Method();
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        // Intentionally not populating brapi terms

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", List.of(trait1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        // Check for conflict response
        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(1, rowErrors.size(), "Wrong number of row errors returned");
        JsonObject rowError = rowErrors.get(0).getAsJsonObject();

        JsonArray errors = rowError.getAsJsonArray("errors");
        assertTrue(errors.size() > 1, "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertTrue(error.has("field"), "Column field not included in return");
        assertTrue(error.has("errorMessage"), "errorMessage field not included in return");
        assertTrue(error.has("httpStatus"), "httpStatus field not included in return");
        assertTrue(error.has("httpStatusCode"), "httpStatusCode field not included in return");
    }

    public void setBrAPIProperties(Trait trait) {

        // Trait
        trait.setTraitClass("Pheno trait");
        trait.setAttribute("leaf length");
        trait.setDefaultValue("2.0");
        trait.setMainAbbreviation("abbrev1");
        trait.setSynonyms(List.of("test1", "test2"));

        // Method
        trait.getMethod().setMethodClass("Estimation");
        trait.getMethod().setDescription("A method");
        trait.getMethod().setFormula("a^2 + b^2 = c^2");

        // Scale
        trait.getScale().setValidValueMin(1);
        trait.getScale().setValidValueMax(10);
        trait.getScale().setDecimalPlaces(3);
        trait.getScale().setCategories(List.of(new BrAPIScaleValidValuesCategories().label("label1").value("value1"),
                                               new BrAPIScaleValidValuesCategories().label("label2").value("value2")));
    }

    //endregion
    //region GET Traits
    @Test
    @Order(5)
    public void getTraitsSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        Boolean trait1Found = false;
        Boolean trait2Found = false;
        for (JsonElement traitJson: data) {
            JsonObject trait = (JsonObject) traitJson;
            String traitId = trait.get("id").getAsString();
            if (traitId.equals(validTraits.get(0).getId().toString())){
                trait1Found = true;
                checkTraitResponse(trait, validTraits.get(0));
            } else if (traitId.equals(validTraits.get(1).getId().toString())) {
                trait2Found = true;
                checkTraitResponse(trait, validTraits.get(1));
            }
        }

        if (!trait1Found || !trait2Found){
            throw new AssertionFailedError("Both traits were not returned");
        }
    }

    @Test
    @SneakyThrows
    @Order(5)
    public void getTraitsFullSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=true").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        Boolean trait1Found = false;
        Boolean trait2Found = false;
        for (JsonElement traitJson: data) {
            JsonObject trait = (JsonObject) traitJson;
            String traitId = trait.get("id").getAsString();
            if (traitId.equals(validTraits.get(0).getId().toString())){
                trait1Found = true;
                checkTraitFullResponse(trait, validTraits.get(0));
            } else if (traitId.equals(validTraits.get(1).getId().toString())) {
                trait2Found = true;
                checkTraitFullResponse(trait, validTraits.get(1));
            }
        }

        if (!trait1Found || !trait2Found){
            throw new AssertionFailedError("Both traits were not returned");
        }

    }

    @Test
    @Order(5)
    public void getTraitsProgramNotExist() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + invalidUUID + "/traits").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    public void getTraitsBadFullQueryParam() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=no").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    @SneakyThrows
    @Order(5)
    public void getTraitSingleSuccess() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTraits.get(0).getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        checkTraitFullResponse(result, validTraits.get(0));
    }

    @Test
    @Order(5)
    public void getTraitSingleNoExist() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + invalidUUID).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(5)
    public void getTraitSingleProgramNotExist() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + invalidUUID + "/traits/" + validTraits.get(0).getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(5)
    public void editableTraitNotExist() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + invalidUUID + "/editable")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(5)
    public void editableProgramNotExist() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + invalidUUID + "/traits/" + validTraits.get(0).getId() + "/editable")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    @Order(6)
    public void editableNoObservations() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTraits.get(0).getId() + "/editable")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(true, result.getAsJsonPrimitive("editable").getAsBoolean(), "expected to be editable");
    }

    @Test
    @Order(7)
    @SneakyThrows
    public void editableHasObservations() {

        // add observation and make sure not editable
        BrAPIClient brapiClient = new BrAPIClient(getProperties().get("brapi.server.pheno-url"));
        ObservationsApi obsApi = new ObservationsApi(brapiClient);
        ObservationVariablesApi varApi = new ObservationVariablesApi(brapiClient);

        VariableQueryParams params = VariableQueryParams.builder()
                .externalReferenceID(validTraits.get(0).getId().toString()).build();

        ApiResponse<BrAPIObservationVariableListResponse> res = varApi.variablesGet(params);
        BrAPIObservationVariableListResponse list = res.getBody();
        List<BrAPIObservationVariable> vars = list.getResult().getData();

        BrAPIObservation observation = new BrAPIObservation().observationVariableDbId(vars.get(0).getObservationVariableDbId());
        ApiResponse<BrAPIObservationListResponse> obsRes = obsApi.observationsPost(List.of(observation));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTraits.get(0).getId() + "/editable")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(false, result.getAsJsonPrimitive("editable").getAsBoolean(), "expected not to be editable");

    }

    @Test
    @Order(8)
    public void editableNotAllowed() {

        // try updating a trait that has observations

        Flowable<HttpResponse<String>> call1 = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTraits.get(0).getId())
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response1 = call1.blockingFirst();
        assertEquals(HttpStatus.OK, response1.getStatus());

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/" + validProgram.getId() + "/traits", List.of(validTraits.get(0)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, e.getStatus());

    }

    @Test
    @SneakyThrows
    @Order(9)
    public void getTraitsExistsInBrAPINotInSystem() {

        dsl.execute(fp.get("DeleteTrait"));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=true").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        assertEquals(0, data.size(), "Number of traits returned is incorrect.");
    }

    public void checkTraitResponse(JsonObject traitJson, Trait trait) {

        if (trait.getId() != null){
            assertEquals(trait.getId().toString(), traitJson.get("id").getAsString(), "Ids don't match");
        }
        assertEquals(trait.getObservationVariableName(), traitJson.get("observationVariableName").getAsString(),"Names don't match");

        if (trait.getActive() != null){
            assertEquals(trait.getActive().toString(), traitJson.get("active").getAsString(), "Actives don't match");
        }

        JsonObject scale = traitJson.getAsJsonObject("scale");
        assertEquals(trait.getScale().getScaleName(), scale.get("scaleName").getAsString(), "Scale names don't match");
        assertEquals(trait.getScale().getDataType().toString(), scale.get("dataType").getAsString(), "Scale data types don't match");

        JsonObject programOntology = traitJson.getAsJsonObject("programOntology");
        if (trait.getProgramOntologyId() != null){
            assertEquals(trait.getProgramOntologyId().toString(), programOntology.get("id").getAsString(), "Program Ontology ids don't match");
        }
    }

    public void checkTraitFullResponse(JsonObject traitJson, Trait trait) {

        // Check values from our db
        checkTraitResponse(traitJson, trait);

        assertEquals(trait.getTraitClass(), traitJson.get("traitClass").getAsString(), "Trait classes don't match");

        assertEquals(trait.getMainAbbreviation(), traitJson.get("mainAbbreviation").getAsString(), "Trait main abbreviations don't match");
        if (trait.getAttribute() != null){
            assertEquals(trait.getAttribute(), traitJson.get("attribute").getAsString(), "Trait attributes don't match");
        }
        // Entity is the program observation level in our system
        //assertEquals(trait.getEntity(), traitJson.get("entity").getAsString(), "Trait entities don't match");
        if (trait.getActive() != null){
            String status = traitJson.get("active").getAsBoolean() ? "active" : "inactive";
            assertEquals(trait, status, "Trait actives don't match");
        }

        List<String> jsonSynonyms = new ArrayList<>();
        traitJson.get("synonyms").getAsJsonArray().iterator().forEachRemaining(element -> jsonSynonyms.add(element.getAsString()));
        List<String> traitSynonyms = new ArrayList<>(trait.getSynonyms());
        Collections.sort(jsonSynonyms);
        Collections.sort(traitSynonyms);
        assertLinesMatch(traitSynonyms, jsonSynonyms, "Synonyms don't match");

        // Check method
        JsonObject methodJson = traitJson.getAsJsonObject("method");
        assertEquals(trait.getMethod().getMethodClass(), methodJson.get("methodClass").getAsString(), "Method classes don't match");

        // Check scale
        JsonObject scaleJson = traitJson.getAsJsonObject("scale");
        assertEquals(trait.getScale().getDecimalPlaces(), scaleJson.get("decimalPlaces").getAsInt(), "Scale decimal places don't match");
        assertEquals(trait.getScale().getDataType().toString(), scaleJson.get("dataType").getAsString(), "Scale data types don't match");

        List<JsonObject> jsonCategories = new ArrayList<>();
        scaleJson.get("categories").getAsJsonArray().iterator().forEachRemaining(element -> jsonCategories.add(element.getAsJsonObject()));
        Collections.sort(jsonCategories, Comparator.comparing((x) -> x.get("label").getAsString()));
        List<BrAPIScaleValidValuesCategories> brApiScaleCategories = new ArrayList<>(trait.getScale().getCategories());
        Collections.sort(brApiScaleCategories, Comparator.comparing(BrAPIScaleValidValuesCategories::getLabel));
        Iterator<BrAPIScaleValidValuesCategories> categories = brApiScaleCategories.iterator();
        for (JsonObject jsonCategory: jsonCategories){
            BrAPIScaleValidValuesCategories category = categories.next();
            assertEquals(category.getLabel(), jsonCategory.get("label").getAsString(), "Category label does not match");
            assertEquals(category.getValue(), jsonCategory.get("value").getAsString(), "Category label does not match");
        }
    }
    //endregion

    @Test
    @Order(10)
    public void postTraitNominalMissingCategoryValue() {

        Trait trait1 = new Trait();
        trait1.setTraitDescription("trait 1 description");
        trait1.setEntity("entity1");
        trait1.setObservationVariableName("Test Trait14");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.NOMINAL);
        Method method1 = new Method();
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        // Set the brapi properties
        setBrAPIProperties(trait1);

        // Set a bad scale
        trait1.getScale().setCategories(List.of(new BrAPIScaleValidValuesCategories()));

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", List.of(trait1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        // Check for conflict response
        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(1, rowErrors.size(), "Wrong number of row errors returned");
        JsonObject rowError = rowErrors.get(0).getAsJsonObject();

        JsonArray errors = rowError.getAsJsonArray("errors");
        assertEquals(1, errors.size(), "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertTrue(error.has("field"), "Column field not included in return");
        assertTrue(error.has("errorMessage"), "errorMessage field not included in return");
        assertTrue(error.has("httpStatus"), "httpStatus field not included in return");
        assertTrue(error.has("httpStatusCode"), "httpStatusCode field not included in return");
        assertTrue(error.has("rowErrors"), "Individual category errors not included in the response");
        // Check individual category errors are present
        JsonArray categoryErrors = error.getAsJsonArray("rowErrors");
        assertEquals(1, categoryErrors.size(), "Not enough errors were returned");
        JsonObject categoryError = errors.get(0).getAsJsonObject();
        assertTrue(categoryError.has("field"), "Column field not included in return");
        assertTrue(categoryError.has("errorMessage"), "errorMessage field not included in return");
        assertTrue(categoryError.has("httpStatus"), "httpStatus field not included in return");
        assertTrue(categoryError.has("httpStatusCode"), "httpStatusCode field not included in return");
    }

    @Test
    @Order(10)
    public void postTraitOrdinalMissingCategoryVariables() {

        Trait trait1 = new Trait();
        trait1.setTraitDescription("trait 1 description");
        trait1.setEntity("entity1");
        trait1.setObservationVariableName("Test Trait14");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.ORDINAL);
        Method method1 = new Method();
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        // Set the brapi properties
        setBrAPIProperties(trait1);

        // Set a bad scale
        trait1.getScale().setCategories(
                List.of(
                        new BrAPIScaleValidValuesCategories().label("1"),
                        new BrAPIScaleValidValuesCategories().label("1").value("test"),
                        new BrAPIScaleValidValuesCategories().value("badtest")
                )
        );

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", List.of(trait1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        // Check for conflict response
        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(1, rowErrors.size(), "Wrong number of row errors returned");
        JsonObject rowError = rowErrors.get(0).getAsJsonObject();

        JsonArray errors = rowError.getAsJsonArray("errors");
        assertEquals(1, errors.size(), "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertTrue(error.has("field"), "Column field not included in return");
        assertTrue(error.has("errorMessage"), "errorMessage field not included in return");
        assertTrue(error.has("httpStatus"), "httpStatus field not included in return");
        assertTrue(error.has("httpStatusCode"), "httpStatusCode field not included in return");
        assertTrue(error.has("rowErrors"), "Individual category errors not included in the response");

        // Check individual category errors are present
        JsonArray categoryErrors = error.getAsJsonArray("rowErrors");
        assertEquals(2, categoryErrors.size(), "Not enough errors were returned");

        JsonObject categoryError = categoryErrors.get(0).getAsJsonObject();
        assertEquals(0, categoryError.get("rowIndex").getAsInt(), "wrong error row index returned");
        JsonObject valueError = categoryError.getAsJsonArray("errors").get(0).getAsJsonObject();
        assertEquals("scale.categories.value", valueError.get("field").getAsString(), "wrong error returned");

        JsonObject secondCategoryError = categoryErrors.get(1).getAsJsonObject();
        assertEquals(2, secondCategoryError.get("rowIndex").getAsInt(), "wrong error row index returned");
        JsonObject labelError = secondCategoryError.getAsJsonArray("errors").get(0).getAsJsonObject();
        assertEquals("scale.categories.label", labelError.get("field").getAsString(), "wrong error returned");
    }

    @Test
    @Order(11)
    public void getTraitsQuery() {
        List<Trait> newTraits = new ArrayList<>();
        for (int i = 0; i < 30; i++){
            Trait trait = new Trait();
            trait.setTraitDescription("trait 1 description");
            trait.setEntity("entity1");
            trait.setAttribute("attribute1");
            trait.setObservationVariableName("Test Trait" + i);
            trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
            Scale scale = new Scale();
            scale.setScaleName("Test Scale" + i);
            scale.setDataType(DataType.TEXT);
            Method method = new Method();
            method.setDescription("Another method" + i);
            method.setMethodClass("Estimation");
            trait.setScale(scale);
            trait.setMethod(method);

            newTraits.add(trait);
        }

        Flowable<HttpResponse<String>> createCall = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", newTraits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> createResponse = createCall.blockingFirst();
        assertEquals(HttpStatus.OK, createResponse.getStatus());

        List<TraitEntity> allTraits = traitDao.findAll();

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?sortField=name&sortOrder=DESC&full=true").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.get("data").getAsJsonArray();

        assertEquals(allTraits.size(), data.size(), "Wrong page size");
        TestUtils.checkStringSorting(data, "observationVariableName", SortOrder.DESC);

        JsonObject pagination = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata").getAsJsonObject("pagination");
        assertEquals(1, pagination.get("totalPages").getAsInt(), "Wrong number of pages");
        assertEquals(allTraits.size(), pagination.get("totalCount").getAsInt(), "Wrong total count");
        assertEquals(1, pagination.get("currentPage").getAsInt(), "Wrong current page");
    }

    @Test
    @Order(12)
    public void searchTraits() {

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilters(new ArrayList<>());
        searchRequest.getFilters().add(new FilterRequest("abbreviations", "t1"));

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits/search?page=1&pageSize=20&sortField=name&sortOrder=ASC", searchRequest).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.get("data").getAsJsonArray();

        // Expect t1, user10->user19
        assertEquals(11, data.size(), "Wrong page size");
        TestUtils.checkStringSorting(data, "observationVariableName", SortOrder.ASC);
    }

    @Test
    @Order(13)
    public void postTraitComputation() {

        dsl.execute(fp.get("DeleteTrait"));

        Trait trait1 = new Trait();
        trait1.setTraitDescription("trait 1 description");
        trait1.setEntity("entity1");
        trait1.setObservationVariableName("Test Trait5");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.TEXT);
        Method method1 = new Method();
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        // Set the brapi properties
        setBrAPIProperties(trait1);

        // Set scale class to computation
        trait1.getMethod().setMethodClass("Computation");

        List<Trait> traits = List.of(trait1);

        // Call endpoint
        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", traits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        JsonObject trait = data.get(0).getAsJsonObject();

        // Check class was overwritten to numerical
        JsonObject scale = trait.get("scale").getAsJsonObject();
        assertEquals(DataType.NUMERICAL.getLiteral(), scale.get("dataType").getAsString(), "wrong scale dataType");

        validTraits = new ArrayList<>();
        trait1.setId(UUID.fromString(trait.get("id").getAsString()));
        validTraits.add(trait1);
    }

    @Test
    @Order(14)
    public void putTraitComputation() {

        Trait updateTrait = validTraits.get(0);

        updateTrait.setTraitDescription("Updated description");
        updateTrait.setEntity("Updated entity");
        updateTrait.setObservationVariableName("Updated name");
        updateTrait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Updated level").build());
        updateTrait.getScale().setScaleName("Updated Scale");
        updateTrait.getScale().setDataType(DataType.DATE);
        updateTrait.getMethod().setDescription("A method");

        // Set scale class to computation
        updateTrait.getMethod().setMethodClass("Observation");

        List<Trait> traits = List.of(updateTrait);

        // Call endpoint
        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/" + validProgram.getId() + "/traits", traits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // Check return
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        JsonObject trait = data.get(0).getAsJsonObject();

        assertEquals(updateTrait.getObservationVariableName(), trait.get("observationVariableName").getAsString(), "wrong trait name");
        assertEquals(updateTrait.getProgramObservationLevel().getName(),
                trait.get("programObservationLevel").getAsJsonObject().get("name").getAsString(), "wrong observation level");
        assertEquals(updateTrait.getScale().getScaleName(),
                trait.get("scale").getAsJsonObject().get("scaleName").getAsString(), "wrong scale name");
        assertEquals(updateTrait.getScale().getDataType().toString(),
                trait.get("scale").getAsJsonObject().get("dataType").getAsString(), "wrong scale data type");
        assertEquals(updateTrait.getMethod().getDescription(),
                trait.get("method").getAsJsonObject().get("description").getAsString(), "wrong method description");
        assertEquals(updateTrait.getMethod().getMethodClass(), trait.get("method").getAsJsonObject().get("methodClass").getAsString(), "wrong method class");

    }

    @Test
    @Order(14)
    public void putTraitMultipleValidationErrors() {

        Trait updateTrait = validTraits.get(0);

        updateTrait.setObservationVariableName(null);

        // Set scale class to computation
        updateTrait.getMethod().setMethodClass("Observation");

        Trait badIdTrait = new Trait();
        badIdTrait.setId(UUID.randomUUID());
        badIdTrait.setObservationVariableName("Bad Trait");
        badIdTrait.setAttribute("attribute");
        badIdTrait.setEntity("entity");
        badIdTrait.setTraitDescription("trait description");
        badIdTrait.setObservationVariableName("trait name");
        badIdTrait.setScale(updateTrait.getScale());
        badIdTrait.setMethod(updateTrait.getMethod());
        badIdTrait.setProgramObservationLevel(updateTrait.getProgramObservationLevel());

        List<Trait> traits = List.of(updateTrait, badIdTrait);

        // Call endpoint
        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/" + validProgram.getId() + "/traits", traits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(2, rowErrors.size(), "Wrong number of row errors returned");

        JsonObject rowError = rowErrors.get(0).getAsJsonObject();
        JsonArray errors = rowError.getAsJsonArray("errors");
        assertEquals(1, errors.size(), "Not enough errors were returned");
        JsonObject error1 = errors.get(0).getAsJsonObject();
        assertEquals("observationVariableName", error1.get("field").getAsString(), "wrong error returned");

        JsonObject badIdRowError = rowErrors.get(1).getAsJsonObject();
        errors = badIdRowError.getAsJsonArray("errors");
        assertEquals(1, errors.size(), "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertEquals("traitId", error.get("field").getAsString(), "wrong error returned");
    }

    @Test
    @Order(15)
    public void archiveTrait() {

        Trait updateTrait = validTraits.get(0);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/" + validProgram.getId() + "/traits/" + updateTrait.getId().toString() + "/archive", new HashMap<>())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");
        assertFalse(result.get("active").getAsBoolean(), "Trait not archived");
    }

    @Test
    @Order(15)
    public void restoreTrait() {

        Trait updateTrait = validTraits.get(0);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/" + validProgram.getId() + "/traits/" + updateTrait.getId().toString() + "/archive?active=true", new HashMap<>())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");
        assertTrue(result.get("active").getAsBoolean(), "Trait not archived");
    }

    @Test
    @Order(16)
    public void putTraitIdDoesNotExist() {

        Trait updateTrait = validTraits.get(0);

        updateTrait.setId(UUID.randomUUID());
        updateTrait.setObservationVariableName("Update Name");
        updateTrait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Updated level").build());
        updateTrait.getScale().setScaleName("Updated Scale");
        updateTrait.getScale().setDataType(DataType.DATE);
        updateTrait.getMethod().setDescription("A method");

        // Set scale class to computation
        updateTrait.getMethod().setMethodClass("Observation");

        List<Trait> traits = List.of(updateTrait);

        // Call endpoint
        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/" + validProgram.getId() + "/traits", traits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(1, rowErrors.size(), "Wrong number of row errors returned");
        JsonObject rowError = rowErrors.get(0).getAsJsonObject();

        JsonArray errors = rowError.getAsJsonArray("errors");
        assertEquals(1, errors.size(), "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertEquals("traitId", error.get("field").getAsString(), "wrong error returned");
    }

    @Test
    @Order(17)
    public void archiveTraitIdNotExist() {

        Trait updateTrait = validTraits.get(0);

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/" + validProgram.getId() + "/traits/" + updateTrait.getId().toString() + "/archive?active=false", new HashMap<>())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());

    }





}
