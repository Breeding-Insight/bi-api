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
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIServerInfo;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.response.BrAPIStudyListResponse;
import org.brapi.v2.model.core.response.BrAPIStudySingleResponse;
import org.brapi.v2.model.pheno.*;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableSingleResponse;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.User;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrAPIV2ControllerIntegrationTest extends BrAPITest {

    @Inject
    @Client("/")
    RxHttpClient biClient;

    @Property(name = "micronaut.bi.api.version")
    String biApiVersion;

    private FannyPack fp;

    private Gson GSON = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
                                         .create();

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private UserDAO userDAO;

    private ProgramEntity validProgram;

    @AfterAll
    public void finish() { super.stopContainers(); }

    @BeforeAll
    @SneakyThrows
    public void setup() {
        fp = FannyPack.fill("src/test/resources/sql/BrapiObservationVariablesControllerIntegrationTest.sql");
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");

        // Insert system roles
        User testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID)
                               .get();
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"),
                    testUser.getId()
                            .toString());

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert program observation level
        dsl.execute(fp.get("InsertProgramObservationLevel"));

        // Insert program ontology sql
        dsl.execute(fp.get("InsertProgramOntology"));
        dsl.execute(fp.get("InsertTestProgramUser"));
        dsl.execute(fp.get("InsertOtherTestProgramUser"));

        // Retrieve our new data
        validProgram = programDao.findAll()
                                 .stream()
                                 .filter(programEntity -> programEntity.getName()
                                                                       .equals("Test Program"))
                                 .findFirst()
                                 .get();

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"),
                    testUser.getId()
                            .toString(),
                    validProgram.getId()
                                .toString());

        var brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        super.getBrapiDsl()
             .execute(brapiFp.get("InsertSpecies"));
    }

    @Test
    public void testRootServerInfo() {
        Flowable<HttpResponse<String>> call = biClient.exchange(GET("/brapi/v2/serverinfo"), String.class);

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.body(), "Response body is empty");

        JsonObject result = JsonParser.parseString(response.body())
                                      .getAsJsonObject()
                                      .getAsJsonObject("result");
        BrAPIServerInfo serverInfo = GSON.fromJson(result, BrAPIServerInfo.class);

        assertEquals("Breeding Insight Platform", serverInfo.getOrganizationName());
        assertEquals("bi-api", serverInfo.getServerName());
        assertEquals("bidevteam@cornell.edu", serverInfo.getContactEmail());
        assertEquals("breedinginsight.org", serverInfo.getOrganizationURL());
    }

    @Test
    @SneakyThrows
    public void testPostGetVariablesProxy() {
        BrAPIObservationVariable variable = generateVariable();

        Flowable<HttpResponse<String>> postCall = biClient.exchange(
                POST(String.format("%s/programs/%s/brapi/v2/variables",
                                   biApiVersion,
                                   validProgram.getId().toString()), Arrays.asList(variable))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpResponse<String> postResponse;
        try {
            postResponse = postCall.blockingFirst();
        } catch (Exception e) {
            throw new Exception(e);
        }
        //check the POST call was successful
        assertEquals(HttpStatus.OK, postResponse.getStatus());

        BrAPIObservationVariable createdVariable = GSON.fromJson(postResponse.body(), BrAPIObservationVariableListResponse.class)
                                                       .getResult()
                                                       .getData()
                                                       .get(0);

        //and that a variable is returned
        assertNotNull(createdVariable);
        //and that the variable has been assigned an ID
        assertNotNull(createdVariable.getObservationVariableDbId(), "observationVariableDbId is null");

        Flowable<HttpResponse<String>> getCall = biClient.exchange(
                GET(String.format("%s/programs/%s/brapi/v2/variables/%s",
                                  biApiVersion,
                                  validProgram.getId().toString(),
                                  createdVariable.getObservationVariableDbId()))
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpResponse<String> getResponse;
        try {
            getResponse = getCall.blockingFirst();
        } catch (Exception e) {
            throw new Exception(e);
        }
        assertEquals(HttpStatus.OK, getResponse.getStatus());
        assertNotNull(getResponse.body(), "Response body is empty");
        BrAPIObservationVariableSingleResponse brAPIObservationVariableSingleResponse = GSON.fromJson(getResponse.body(), BrAPIObservationVariableSingleResponse.class);

        BrAPIObservationVariable fetchedVariable = brAPIObservationVariableSingleResponse.getResult();
        assertNotNull(fetchedVariable, "Observation Variable was not found");
        assertEquals(createdVariable.getObservationVariableDbId(), fetchedVariable.getObservationVariableDbId());
        //make sure the original values sent in the POST were saved correctly
        assertEquals(variable.getObservationVariableName(), fetchedVariable.getObservationVariableName());
        assertEquals(variable.getExternalReferences(), fetchedVariable.getExternalReferences());
        assertEquals(variable.getCommonCropName(), fetchedVariable.getCommonCropName());

        assertNotNull(fetchedVariable.getTrait(), "Trait is null");
        assertEquals(createdVariable.getTrait().getTraitDbId(), fetchedVariable.getTrait().getTraitDbId());
        //make sure the original values sent in the POST were saved correctly
        assertEquals(variable.getTrait().getTraitName(), fetchedVariable.getTrait().getTraitName());
        assertEquals(variable.getTrait().getTraitClass(), fetchedVariable.getTrait().getTraitClass());


        assertNotNull(fetchedVariable.getMethod(), "Method is null");
        assertEquals(createdVariable.getMethod().getMethodDbId(), fetchedVariable.getMethod().getMethodDbId());
        //make sure the original values sent in the POST were saved correctly
        assertEquals(variable.getMethod().getMethodName(), fetchedVariable.getMethod().getMethodName());
        assertEquals(variable.getMethod().getMethodClass(), fetchedVariable.getMethod().getMethodClass());

        assertNotNull(fetchedVariable.getScale(), "Scale is null");
        assertEquals(createdVariable.getScale().getScaleDbId(), fetchedVariable.getScale().getScaleDbId());
        //make sure the original values sent in the POST were saved correctly
        assertEquals(variable.getScale().getScaleName(), fetchedVariable.getScale().getScaleName());

        Flowable<HttpResponse<String>> getScaleCall = biClient.exchange(
                GET(String.format("%s/programs/%s/brapi/v2/scales/%s",
                                  biApiVersion,
                                  validProgram.getId().toString(),
                                  createdVariable.getScale().getScaleDbId()))
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpResponse<String> getScaleResponse;
        try {
            getScaleResponse = getScaleCall.blockingFirst();
        } catch (Exception e) {
            throw new Exception(e);
        }
        assertEquals(HttpStatus.OK, getScaleResponse.getStatus());

        BrAPIScale scaleResponse = GSON.fromJson(JsonParser.parseString(getScaleResponse.body()).getAsJsonObject().getAsJsonObject("result"), BrAPIScale.class);

        //TODO this is not being returned
//        assertEquals(variable.getScale().getDataType(), scaleResponse.getDataType());
    }

    @Test
    @SneakyThrows
    public void testPutVariablesProxy() {
        BrAPIObservationVariable variable = generateVariable();

        Flowable<HttpResponse<String>> postCall = biClient.exchange(
                POST(String.format("%s/programs/%s/brapi/v2/variables",
                                   biApiVersion,
                                   validProgram.getId().toString()), Arrays.asList(variable))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpResponse<String> postResponse;
        try {
            postResponse = postCall.blockingFirst();
        } catch (Exception e) {
            throw new Exception(e);
        }
        //check the POST call was successful
        assertEquals(HttpStatus.OK, postResponse.getStatus());

        BrAPIObservationVariable createdVariable = GSON.fromJson(postResponse.body(), BrAPIObservationVariableListResponse.class)
                                                       .getResult()
                                                       .getData()
                                                       .get(0);

        createdVariable.setObservationVariableName("Updated variable name");


        Flowable<HttpResponse<String>> putCall = biClient.exchange(
                PUT(String.format("%s/programs/%s/brapi/v2/variables/%s",
                                   biApiVersion,
                                   validProgram.getId().toString(), createdVariable.getObservationVariableDbId()), variable)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpResponse<String> putResponse;
        try {
            putResponse = putCall.blockingFirst();
        } catch (Exception e) {
            throw new Exception(e);
        }
        //check the PUT call was successful
        assertEquals(HttpStatus.OK, putResponse.getStatus());

        Flowable<HttpResponse<String>> getCall = biClient.exchange(
                GET(String.format("%s/programs/%s/brapi/v2/variables/%s",
                                  biApiVersion,
                                  validProgram.getId().toString(),
                                  createdVariable.getObservationVariableDbId()))
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpResponse<String> getResponse;
        try {
            getResponse = getCall.blockingFirst();
        } catch (Exception e) {
            throw new Exception(e);
        }
        assertEquals(HttpStatus.OK, getResponse.getStatus());
        BrAPIObservationVariableSingleResponse brAPIObservationVariableSingleResponse = GSON.fromJson(getResponse.body(), BrAPIObservationVariableSingleResponse.class);

        BrAPIObservationVariable fetchedVariable = brAPIObservationVariableSingleResponse.getResult();
        //make sure the updated value persisted
        assertEquals(variable.getObservationVariableName(), fetchedVariable.getObservationVariableName());
    }

    @Test
    @SneakyThrows
    public void testPostGetStudiesProxy() {
        BrAPIStudy study = new BrAPIStudy().studyName("test study")
                                           .studyCode("123")
                                           .studyType("Phenotyping Trial")
                                           .studyDescription("Test study description")
                .active(true)
                .startDate(OffsetDateTime.of(2021, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC))
                                           .endDate(OffsetDateTime.of(2021, 2, 5, 0, 0, 0, 0, ZoneOffset.UTC));

        Flowable<HttpResponse<String>> postCall = biClient.exchange(
                POST(String.format("%s/programs/%s/brapi/v2/studies",
                                   biApiVersion,
                                   validProgram.getId().toString()), Arrays.asList(study))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpResponse<String> postResponse;
        try {
            postResponse = postCall.blockingFirst();
        } catch (Exception e) {
            throw new Exception(e);
        }
        //check the POST call was successful
        assertEquals(HttpStatus.OK, postResponse.getStatus());

        BrAPIStudy createdStudy = GSON.fromJson(postResponse.body(), BrAPIStudyListResponse.class)
                                                       .getResult()
                                                       .getData()
                                                       .get(0);

        //and that a study is returned
        assertNotNull(createdStudy);
        //and that the study has been assigned an ID
        assertNotNull(createdStudy.getStudyDbId(), "studyDbId is null");

        Flowable<HttpResponse<String>> getCall = biClient.exchange(
                GET(String.format("%s/programs/%s/brapi/v2/studies/%s",
                                  biApiVersion,
                                  validProgram.getId().toString(),
                                  createdStudy.getStudyDbId()))
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpResponse<String> getResponse;
        try {
            getResponse = getCall.blockingFirst();
        } catch (Exception e) {
            throw new Exception(e);
        }
        assertEquals(HttpStatus.OK, getResponse.getStatus());
        assertNotNull(getResponse.body(), "Response body is empty");

        BrAPIStudy fetchedStudy = GSON.fromJson(getResponse.body(), BrAPIStudySingleResponse.class).getResult();

        assertEquals(study.getStudyName(), fetchedStudy.getStudyName());
        assertEquals(study.getStudyCode(), fetchedStudy.getStudyCode());
        assertEquals(study.getStudyType(), fetchedStudy.getStudyType());
        assertEquals(study.getStudyDescription(), fetchedStudy.getStudyDescription());
        assertEquals(study.isActive(), fetchedStudy.isActive());
        assertEquals(study.getStartDate(), fetchedStudy.getStartDate());
        assertEquals(study.getEndDate(), fetchedStudy.getEndDate());
    }

    private BrAPIObservationVariable generateVariable() {
        var random = UUID.randomUUID()
                         .toString();
        return new BrAPIObservationVariable().observationVariableName("test" + random)
                                             .commonCropName("Grape")
                                             .externalReferences(Collections.singletonList(new BrAPIExternalReference().referenceID("abc123")
                                                                                                                       .referenceSource("breedinginsight.org")))
                                             .trait(new BrAPITrait().traitClass("Agronomic")
                                                                    .traitName("test trait" + random))
                                             .method(new BrAPIMethod().methodName("test method" + random)
                                                                      .methodClass("Measurement"))
                                             .scale(new BrAPIScale().scaleName("test scale" + random)
                                                                    .dataType(BrAPITraitDataType.NUMERICAL));
    }
}
