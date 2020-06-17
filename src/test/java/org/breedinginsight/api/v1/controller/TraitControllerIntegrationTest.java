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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.brapi.client.v2.model.exceptions.HttpInternalServerError;
import org.brapi.client.v2.model.exceptions.HttpNotFoundException;
import org.brapi.client.v2.modules.phenotype.VariablesAPI;
import org.brapi.v2.core.model.BrApiExternalReference;
import org.brapi.v2.phenotyping.model.*;
import org.brapi.v2.phenotyping.model.request.VariablesRequest;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.dao.db.tables.daos.*;
import org.breedinginsight.dao.db.tables.pojos.*;
import org.breedinginsight.model.*;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.inject.Inject;
import java.util.*;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.mockito.Mockito.*;


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TraitControllerIntegrationTest {

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private TraitDao traitDao;
    @Inject
    private ProgramOntologyDao programOntologyDao;
    @Inject
    private MethodDao methodDao;
    @Inject
    private ScaleDao scaleDao;
    @Mock
    private VariablesAPI variablesAPI;

    @Inject
    private BrAPIProvider brAPIProvider;
    @MockBean(BrAPIProvider.class)
    BrAPIProvider brAPIProvider() { return mock(BrAPIProvider.class); }

    private Trait validTrait;
    private List<Trait> validTraits;
    private ProgramEntity validProgram;
    private String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @BeforeAll
    @SneakyThrows
    public void setup() {

        MockitoAnnotations.initMocks(this);

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

        // Insert method
        dsl.execute(fp.get("InsertMethod1"));

        // Insert scale
        dsl.execute(fp.get("InsertScale1"));

        // Insert trait
        dsl.execute(fp.get("InsertTrait1"));

        // Retrieve our new data
        validProgram = programDao.findAll().get(0);
        ProgramOntologyEntity programOntologyEntity = programOntologyDao.fetchByProgramId(validProgram.getId()).get(0);
        List<TraitEntity> traitEntities = traitDao.fetchByProgramOntologyId(programOntologyEntity.getId());
        validTraits = new ArrayList<>();
        for (TraitEntity traitEntity: traitEntities) {
            Trait trait = new Trait(traitEntity);
            MethodEntity methodEntity = methodDao.fetchOneById(trait.getMethodId());
            Method method = new Method(methodEntity);
            ScaleEntity scaleEntity = scaleDao.fetchOneById(trait.getScaleId());
            Scale scale = new Scale(scaleEntity);
            trait.setMethod(method);
            trait.setScale(scale);
            validTraits.add(trait);
        }
    }

    @Test
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
    public void getTraitsFullSuccess() {

        BrApiVariable brApiVariable1 = getTestBrApiVariable(validTraits.get(0).getId(), validTraits.get(0).getMethodId(),
            validTraits.get(0).getScaleId());
        BrApiVariable brApiVariable2 = getTestBrApiVariable(validTraits.get(1).getId(), validTraits.get(1).getMethodId(),
                validTraits.get(1).getScaleId());

        List<BrApiVariable> brApiVariables = List.of(brApiVariable1, brApiVariable2);

        // Mock brapi response
        reset(variablesAPI);
        when(variablesAPI.getVariables(any(VariablesRequest.class))).thenReturn(brApiVariables);
        when(brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO)).thenReturn(variablesAPI);

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
                checkTraitFullResponse(trait, validTraits.get(0), brApiVariable1);
            } else if (traitId.equals(validTraits.get(1).getId().toString())) {
                trait2Found = true;
                checkTraitFullResponse(trait, validTraits.get(1), brApiVariable2);
            }
        }

        if (!trait1Found || !trait2Found){
            throw new AssertionFailedError("Both traits were not returned");
        }

    }

    @Test
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
    public void getTraitsBrAPIError() {

        reset(variablesAPI);
        when(variablesAPI.getVariables()).thenThrow(new HttpNotFoundException("test"));
        when(brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO)).thenReturn(variablesAPI);

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
    public void getTraitsNotExistInBrAPI() {

        BrApiVariable brApiVariable1 = getTestBrApiVariable(validTraits.get(0).getId(), validTraits.get(0).getMethodId(),
                validTraits.get(0).getScaleId());

        List<BrApiVariable> brApiVariables = List.of(brApiVariable1);

        // Mock brapi response
        reset(variablesAPI);
        when(variablesAPI.getVariables()).thenReturn(brApiVariables);
        when(brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO)).thenReturn(variablesAPI);

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
    public void getTraitsExistsInBrAPINotInSystem() {

        BrApiVariable brApiVariable1 = getTestBrApiVariable(validTraits.get(0).getId(), validTraits.get(0).getMethodId(),
                validTraits.get(0).getScaleId());
        BrApiVariable brApiVariable2 = getTestBrApiVariable(validTraits.get(1).getId(), validTraits.get(1).getMethodId(),
                validTraits.get(1).getScaleId());
        BrApiVariable brApiVariable3 = getTestBrApiVariable(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());

        List<BrApiVariable> brApiVariables = List.of(brApiVariable1, brApiVariable2, brApiVariable3);

        // Mock brapi response
        reset(variablesAPI);
        when(variablesAPI.getVariables(any(VariablesRequest.class))).thenReturn(brApiVariables);
        when(brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO)).thenReturn(variablesAPI);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits?full=true").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        assertEquals(2, data.size(), "Number of traits returned is incorrect.");
        checkTraitFullResponse(data.get(0).getAsJsonObject(), validTraits.get(0), brApiVariable1);
        checkTraitFullResponse(data.get(1).getAsJsonObject(), validTraits.get(1), brApiVariable1);
    }

    @Test
    @SneakyThrows
    public void getTraitSingleSuccess() {

        BrApiVariable brApiVariable1 = getTestBrApiVariable(validTraits.get(0).getId(), validTraits.get(0).getMethodId(),
                validTraits.get(0).getScaleId());
        List<BrApiVariable> brApiVariables = List.of(brApiVariable1);

        // Mock brapi response
        reset(variablesAPI);
        when(variablesAPI.getVariables(any(VariablesRequest.class))).thenReturn(brApiVariables);
        when(brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO)).thenReturn(variablesAPI);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTraits.get(0).getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        checkTraitFullResponse(result, validTraits.get(0), brApiVariable1);
    }

    @Test
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
    @SneakyThrows
    public void getTraitSingleNoExistInBrAPI() {

        BrApiVariable brApiVariable1 = getTestBrApiVariable(validTraits.get(0).getId(), validTraits.get(0).getMethodId(),
                validTraits.get(0).getScaleId());
        List<BrApiVariable> brApiVariables = new ArrayList<>();

        // Mock brapi response
        reset(variablesAPI);
        when(variablesAPI.getVariables(any(VariablesRequest.class))).thenReturn(brApiVariables);
        when(brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO)).thenReturn(variablesAPI);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTraits.get(1).getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @Test
    @SneakyThrows
    public void getTraitSingleBrAPIError() {

        reset(variablesAPI);
        when(variablesAPI.getVariables()).thenThrow(new HttpInternalServerError("test"));
        when(brAPIProvider.getVariablesAPI(BrAPIClientType.PHENO)).thenReturn(variablesAPI);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + validProgram.getId() + "/traits/" + validTraits.get(0).getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @Test
    public void getTraitSingleProgramNotExist() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + invalidUUID + "/traits/" + validTraits.get(0).getId()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    public BrApiVariable getTestBrApiVariable(UUID variableId, UUID methodId, UUID scaleId) {
        BrApiExternalReference externalReference = BrApiExternalReference.builder()
                .referenceID(variableId.toString())
                .build();
        List<BrApiExternalReference> externalReferenceList = List.of(externalReference);

        return BrApiVariable.builder()
                .observationVariableDbId("123")
                .contextOfUse(List.of("Nursery Evaluation", "Trial Evaluation"))
                .commonCropName("Tomatillo")
                .defaultValue("defaultValue")
                .documentationURL("http://breedinginsight.org")
                .externalReferences(externalReferenceList)
                .growthStage("flowering")
                .method(getTestBrApiMethod(methodId))
                .scale(getTestBrApiScale(scaleId))
                .scientist("Dr. Jekyll")
                .status("active")
                .trait(getTestBrApiTrait())
                .build();
    }

    public BrApiMethod getTestBrApiMethod(UUID methodId) {
        BrApiExternalReference externalReference = BrApiExternalReference.builder()
                .referenceID(methodId.toString())
                .build();
        List<BrApiExternalReference> externalReferenceList = List.of(externalReference);
        BrApiMethod brApiMethod = BrApiMethod.builder()
                .methodClass("Counting")
                .externalReferences(externalReferenceList)
                .build();
        return brApiMethod;
    }

    public BrApiScale getTestBrApiScale(UUID scaleId) {
        BrApiExternalReference externalReference = BrApiExternalReference.builder()
                .referenceID(scaleId.toString())
                .build();
        List<BrApiExternalReference> externalReferenceList = List.of(externalReference);
        BrApiScaleValidValues brApiScaleValidValues = BrApiScaleValidValues.builder()
                .categories(List.of(
                        BrApiScaleCategories.builder().label("test2").value("value1").build(),
                        BrApiScaleCategories.builder().label("test1").value("value2").build()
                ))
                .build();
        BrApiScale brApiScale = BrApiScale.builder()
                .decimalPlaces(3)
                .dataType(BrApiTraitDataType.TEXT)
                .validValues(brApiScaleValidValues)
                .externalReferences(externalReferenceList)
                .build();
        return brApiScale;
    }

    public BrApiTrait getTestBrApiTrait() {
        BrApiTrait brApiTrait = BrApiTrait.builder()
                .traitClass("morphological")
                .traitDescription("A trait")
                .alternativeAbbreviations(List.of("t1", "t2"))
                .mainAbbreviation("t1")
                .attribute("height")
                .entity("stalk")
                .status("active")
                .synonyms(List.of("stalk height", "stalk tallness"))
                .build();

        return brApiTrait;
    }

    public void checkTraitResponse(JsonObject traitJson, Trait trait) {

        assertEquals(trait.getId().toString(), traitJson.get("id").getAsString(), "Ids don't match");
        assertEquals(trait.getTraitName(), traitJson.get("traitName").getAsString(),"Names don't match");
        assertEquals(trait.getActive().toString(), traitJson.get("active").getAsString(), "Actives don't match");

        JsonObject method = traitJson.getAsJsonObject("method");
        assertEquals(trait.getMethod().getMethodName(), method.get("methodName").getAsString(), "Method names don't match");

        JsonObject scale = traitJson.getAsJsonObject("scale");
        assertEquals(trait.getScale().getScaleName(), scale.get("scaleName").getAsString(), "Scale names don't match");
        assertEquals(trait.getScale().getDataType().toString(), scale.get("dataType").getAsString(), "Scale data types don't match");

        JsonObject programOntology = traitJson.getAsJsonObject("programOntology");
        assertEquals(trait.getProgramOntologyId().toString(), programOntology.get("id").getAsString(), "Program Ontology ids don't match");
    }

    public void checkTraitFullResponse(JsonObject traitJson, Trait trait, BrApiVariable brApiVariable) {

        // Check values from our db
        checkTraitResponse(traitJson, trait);

        assertEquals(brApiVariable.getTrait().getTraitClass(), traitJson.get("traitClass").getAsString(), "Trait classes don't match");
        assertEquals(brApiVariable.getTrait().getTraitDescription(), traitJson.get("description").getAsString(), "Trait descriptions don't match");

        List<String> jsonAlternativeAbbreviations = new ArrayList<>();
        traitJson.get("abbreviations").getAsJsonArray().iterator().forEachRemaining(element -> jsonAlternativeAbbreviations.add(element.getAsString()));
        List<String> traitAbbreviations = new ArrayList<>(brApiVariable.getTrait().getAlternativeAbbreviations());
        Collections.sort(jsonAlternativeAbbreviations);
        Collections.sort(traitAbbreviations);
        assertLinesMatch(traitAbbreviations, jsonAlternativeAbbreviations, "Alternative abbreviations don't match");

        assertEquals(brApiVariable.getTrait().getMainAbbreviation(), traitJson.get("mainAbbreviation").getAsString(), "Trait main abbreviations don't match");
        assertEquals(brApiVariable.getTrait().getAttribute(), traitJson.get("attribute").getAsString(), "Trait attributes don't match");
        assertEquals(brApiVariable.getTrait().getEntity(), traitJson.get("entity").getAsString(), "Trait entities don't match");
        String status = traitJson.get("active").getAsBoolean() ? "active" : "inactive";
        assertEquals(brApiVariable.getStatus(), status, "Trait actives don't match");

        List<String> jsonSynonyms = new ArrayList<>();
        traitJson.get("synonyms").getAsJsonArray().iterator().forEachRemaining(element -> jsonSynonyms.add(element.getAsString()));
        List<String> traitSynonyms = new ArrayList<>(brApiVariable.getTrait().getSynonyms());
        Collections.sort(jsonSynonyms);
        Collections.sort(traitSynonyms);
        assertLinesMatch(traitSynonyms, jsonSynonyms, "Synonyms don't match");

        // Check method
        JsonObject methodJson = traitJson.getAsJsonObject("method");
        assertEquals(brApiVariable.getMethod().getMethodClass(), methodJson.get("methodClass").getAsString(), "Method classes don't match");

        // Check scale
        JsonObject scaleJson = traitJson.getAsJsonObject("scale");
        assertEquals(brApiVariable.getScale().getDecimalPlaces(), scaleJson.get("decimalPlaces").getAsInt(), "Scale decimal places don't match");
        assertEquals(brApiVariable.getScale().getDataType().toString(), scaleJson.get("dataType").getAsString(), "Scale data types don't match");

        List<JsonObject> jsonCategories = new ArrayList<>();
        scaleJson.get("categories").getAsJsonArray().iterator().forEachRemaining(element -> jsonCategories.add(element.getAsJsonObject()));
        Collections.sort(jsonCategories, Comparator.comparing((x) -> x.get("label").getAsString()));
        List<BrApiScaleCategories> brApiScaleCategories = new ArrayList<>(brApiVariable.getScale().getValidValues().getCategories());
        Collections.sort(brApiScaleCategories, Comparator.comparing(BrApiScaleCategories::getLabel));
        Iterator<BrApiScaleCategories> categories = brApiScaleCategories.iterator();
        for (JsonObject jsonCategory: jsonCategories){
            BrApiScaleCategories category = categories.next();
            assertEquals(category.getLabel(), jsonCategory.get("label").getAsString(), "Category label does not match");
            assertEquals(category.getValue(), jsonCategory.get("value").getAsString(), "Category label does not match");
        }
    }

}
