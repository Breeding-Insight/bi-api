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
import com.google.gson.reflect.TypeToken;
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
import org.brapi.v2.phenotyping.model.*;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.daos.*;
import org.breedinginsight.dao.db.tables.pojos.*;
import org.breedinginsight.model.*;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.util.*;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.jupiter.api.Assertions.*;


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TraitControllerIntegrationTest extends BrAPITest {

    private TraitEntity validTrait;
    private FannyPack fp;

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private TraitDao traitDao;

    private List<Trait> validTraits;
    private ProgramEntity validProgram;
    private String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @BeforeAll
    @SneakyThrows
    public void setup() {

        // Insert our traits into the db
        fp = FannyPack.fill("src/test/resources/sql/TraitControllerIntegrationTest.sql");

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert program observation level
        dsl.execute(fp.get("InsertProgramObservationLevel"));

        // Insert program ontology sql
        dsl.execute(fp.get("InsertProgramOntology"));

        // Insert Trait
        dsl.execute(fp.get("InsertMethod"));
        dsl.execute(fp.get("InsertScale"));
        dsl.execute(fp.get("InsertTrait"));

        // Retrieve our new data
        validProgram = programDao.findAll().get(0);

        // Retrieve our valid trait
        validTrait = traitDao.findAll().get(0);
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
        trait.setTraitName("Test Trait3");
        trait.setDescription("A trait1");
        trait.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale = new Scale();
        scale.setScaleName("Test Scale");
        scale.setDataType(DataType.TEXT);
        Method method = new Method();
        method.setMethodName("Test Method");
        trait.setScale(scale);
        trait.setMethod(method);

        setBrAPIProperties(trait);

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", List.of(trait))
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
        var brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));

        dsl.execute(fp.get("DeleteTrait"));

        Trait trait1 = new Trait();
        trait1.setTraitName("Test Trait");
        trait1.setDescription("A trait1");
        trait1.setAbbreviations(List.of("t1", "t2").toArray(String[]::new));
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.TEXT);
        Method method1 = new Method();
        method1.setMethodName("Test Method");
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        Trait trait2 = new Trait();
        trait2.setTraitName("Test Trait1");
        trait2.setDescription("A trait2");
        trait2.setAbbreviations(List.of("t3", "t4").toArray(String[]::new));
        trait2.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale2 = new Scale();
        scale2.setScaleName("Test Scale1");
        scale2.setDataType(DataType.TEXT);
        Method method2 = new Method();
        method2.setMethodName("Test Method1");
        trait2.setScale(scale2);
        trait2.setMethod(method2);

        // Set the brapi properties
        setBrAPIProperties(trait1);
        setBrAPIProperties(trait2);

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
            String traitName = trait.get("traitName").getAsString();
            if (traitName.equals(trait1.getTraitName())){
                trait1Found = true;
                checkTraitFullResponse(trait, trait1);
                trait1.setId(UUID.fromString(trait.get("id").getAsString()));
            } else if (traitName.equals(trait2.getTraitName())) {
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
    @Order(4)
    public void createTraitsDuplicate() {
        Trait trait1 = new Trait();
        trait1.setTraitName("Test Trait");
        trait1.setDescription("A trait1");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.TEXT);
        Method method1 = new Method();
        method1.setMethodName("Test Method");
        trait1.setScale(scale1);
        trait1.setMethod(method1);
        setBrAPIProperties(trait1);

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
        assertEquals(1, errors.size(), "Wrong number of errors returned");
        JsonObject duplicateError = errors.get(0).getAsJsonObject();
        assertEquals(409, duplicateError.get("httpStatusCode").getAsInt(), "Wrong error code returned");
    }

    @Test
    @Order(4)
    public void createTraitsLevelDoesNotExist() {
        Trait trait1 = new Trait();
        trait1.setTraitName("Test Trait that is unique");
        trait1.setDescription("A trait1");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Not Exist").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.TEXT);
        Method method1 = new Method();
        method1.setMethodName("Test Method");
        trait1.setScale(scale1);
        trait1.setMethod(method1);
        setBrAPIProperties(trait1);

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
        assertEquals(1, errors.size(), "Wrong number of errors returned");
        JsonObject duplicateError = errors.get(0).getAsJsonObject();
        assertEquals(404, duplicateError.get("httpStatusCode").getAsInt(), "Wrong error code returned");
    }

    @Test
    @Order(4)
    public void createTraitsValidationError() {

        Trait trait1 = new Trait();
        trait1.setTraitName("Test Trait3");
        trait1.setDescription("A trait1");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        scale1.setDataType(DataType.TEXT);
        Method method1 = new Method();
        method1.setMethodName("Test Method");
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
        trait.getScale().setCategories(List.of(BrApiScaleCategories.builder().label("label1").value("value1").build(),
                BrApiScaleCategories.builder().label("label2").value("value2").build()));
    }


    public JsonArray constructTraitBody(List<Trait> traits) {
        JsonArray requestBody = new JsonArray();
        for (Trait trait: traits){
            JsonObject jsonTrait = new JsonObject();
            jsonTrait.addProperty("traitName", trait.getTraitName());
            JsonArray abbreviations = (JsonArray) new Gson().toJsonTree(trait.getAbbreviations(),
                    new TypeToken<List<String>>() {
                    }.getType());
            jsonTrait.add("abbreviations", abbreviations);
            jsonTrait.addProperty("traitClass", trait.getTraitClass());
            jsonTrait.addProperty("attribute", trait.getAttribute());
            jsonTrait.addProperty("defaultValue", trait.getDefaultValue());
            jsonTrait.addProperty("entity", trait.getEntity());
            jsonTrait.addProperty("mainAbbreviation", trait.getMainAbbreviation());
            JsonArray synonyms = (JsonArray) new Gson().toJsonTree(trait.getSynonyms(),
                    new TypeToken<List<String>>() {
                    }.getType());
            jsonTrait.add("synonyms", synonyms);

            // Add method
            Method method = trait.getMethod();
            JsonObject jsonMethod = new JsonObject();
            jsonMethod.addProperty("methodName", method.getMethodName());
            jsonMethod.addProperty("methodClass", method.getMethodClass());
            jsonMethod.addProperty("description", method.getDescription());
            jsonMethod.addProperty("formula", method.getFormula());
            jsonTrait.add("method", jsonMethod);

            // Add scale
            Scale scale = trait.getScale();
            JsonObject jsonScale = new JsonObject();
            jsonScale.addProperty("scaleName", scale.getScaleName());
            jsonScale.addProperty("dataType", scale.getDataType().toString());
            jsonScale.addProperty("validValueMin", scale.getValidValueMin());
            jsonScale.addProperty("validValueMax", scale.getValidValueMax());
            jsonScale.addProperty("decimalPlaces", scale.getDecimalPlaces());
            JsonArray categories = (JsonArray) new Gson().toJsonTree(scale.getCategories(),
                    new TypeToken<List<BrApiScaleCategories>>() {
                    }.getType());
            jsonScale.add("categories", categories);
            jsonTrait.add("scale", jsonScale);

            // Add program observation level
            ProgramObservationLevel programObservationLevel = trait.getProgramObservationLevel();
            JsonObject jsonProgramObservationLevel = new JsonObject();
            jsonProgramObservationLevel.addProperty("id", programObservationLevel.getId().toString());
            jsonTrait.add("programObservationLevel", jsonProgramObservationLevel);

            requestBody.add(jsonTrait);
        }

        return requestBody;
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
    @SneakyThrows
    @Order(6)
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
        assertEquals(trait.getTraitName(), traitJson.get("traitName").getAsString(),"Names don't match");

        if (trait.getActive() != null){
            assertEquals(trait.getActive().toString(), traitJson.get("active").getAsString(), "Actives don't match");
        }

        JsonObject method = traitJson.getAsJsonObject("method");
        assertEquals(trait.getMethod().getMethodName(), method.get("methodName").getAsString(), "Method names don't match");

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
        assertEquals(trait.getDescription(), traitJson.get("description").getAsString(), "Trait descriptions don't match");

        List<String> jsonAlternativeAbbreviations = new ArrayList<>();
        traitJson.get("abbreviations").getAsJsonArray().iterator().forEachRemaining(element -> jsonAlternativeAbbreviations.add(element.getAsString()));
        List<String> traitAbbreviations = Arrays.asList(trait.getAbbreviations());
        Collections.sort(jsonAlternativeAbbreviations);
        Collections.sort(traitAbbreviations);
        assertLinesMatch(traitAbbreviations, jsonAlternativeAbbreviations, "Alternative abbreviations don't match");

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
        List<BrApiScaleCategories> brApiScaleCategories = new ArrayList<>(trait.getScale().getCategories());
        Collections.sort(brApiScaleCategories, Comparator.comparing(BrApiScaleCategories::getLabel));
        Iterator<BrApiScaleCategories> categories = brApiScaleCategories.iterator();
        for (JsonObject jsonCategory: jsonCategories){
            BrApiScaleCategories category = categories.next();
            assertEquals(category.getLabel(), jsonCategory.get("label").getAsString(), "Category label does not match");
            assertEquals(category.getValue(), jsonCategory.get("value").getAsString(), "Category label does not match");
        }
    }
    //endregion


}
