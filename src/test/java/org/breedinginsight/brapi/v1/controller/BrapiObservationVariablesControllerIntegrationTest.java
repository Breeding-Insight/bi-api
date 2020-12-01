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
package org.breedinginsight.brapi.v1.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import lombok.SneakyThrows;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrapiObservationVariablesControllerIntegrationTest extends BrAPITest {

    @Inject
    @Client(BrapiVersion.BRAPI_V1)
    RxHttpClient client;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient biClient;

    private FannyPack fp;

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private UserDAO userDAO;

    private List<Trait> programTraits;
    private List<Trait> otherProgramTraits;
    private String observationVariableDbId;

    private ProgramEntity validProgram;
    private ProgramEntity otherValidProgram;

    @BeforeAll
    @SneakyThrows
    public void setup() {

        // Insert our traits into the db
        fp = FannyPack.fill("src/test/resources/sql/BrapiObservationVariablesControllerIntegrationTest.sql");
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");

        // Insert system roles
        User testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        User otherTestUser = userDAO.getUserByOrcId(TestTokenValidator.OTHER_TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert program observation level
        dsl.execute(fp.get("InsertProgramObservationLevel"));

        // Insert program ontology sql
        dsl.execute(fp.get("InsertProgramOntology"));
        dsl.execute(fp.get("InsertTestProgramUser"));
        dsl.execute(fp.get("InsertOtherTestProgramUser"));

        // Retrieve our new data
        validProgram = programDao.findAll().get(0);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), validProgram.getId().toString());

        // Insert other program
        dsl.execute(fp.get("InsertOtherProgram"));
        dsl.execute(fp.get("InsertOtherProgramObservationLevel"));
        dsl.execute(fp.get("InsertOtherProgramOntology"));
        dsl.execute(fp.get("InsertOtherTestOtherProgramUser"));

        otherValidProgram = programDao.fetchByName("Other Test Program").get(0);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), otherValidProgram.getId().toString());
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), otherTestUser.getId().toString(), otherValidProgram.getId().toString());

        // Add species to BrAPI server
        fp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");
        super.getBrapiDsl().execute(fp.get("InsertSpecies"));
    }

    Trait buildTestTrait(String name, String traitClass) {
        Method method = Method.builder()
                .description(name + " method description")
                .methodClass("Observation")
                .build();

        Scale scale = Scale.builder()
                .scaleName(name + " scale")
                .dataType(DataType.NUMERICAL)
                .build();

        ProgramObservationLevel level = ProgramObservationLevel.builder()
                .name("Plant")
                .build();

        Trait trait = Trait.builder()
                .traitName(name)
                .method(method)
                .scale(scale)
                .programObservationLevel(level)
                .traitClass(traitClass)
                .active(true)
                .build();
        return trait;
    }

    void checkTraits(Trait expected, JsonObject actual) {
        JsonObject trait = actual.getAsJsonObject("trait");
        assertEquals(expected.getTraitName(), actual.get("observationVariableName").getAsString(), "Wrong name");
        //TODO: more complete validation after field book workarounds are resolved
    }

    @Test
    void userDoesNotExist() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/variables")
                        .bearerAuth("non-existent-user"), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
    }

    @Test()
    void userNotInAnyPrograms() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/variables")
                        .bearerAuth("another-registered-user"), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        assertEquals(0, data.size(), "Array should be empty");
    }

    @Test
    @Order(2)
    void userInOneProgramWithTraits() {

        // create trait in db and brapi service for program test user is in
        programTraits = List.of(buildTestTrait("Test trait 1", "a"), buildTestTrait("Test trait 2", "b"));

        Flowable<HttpResponse<String>> call = biClient.exchange(
                POST("/programs/" + validProgram.getId() + "/traits", programTraits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(2, data.size(), "Array size should be 2");

        // get brapi variables and check matches
        call = client.exchange(
                GET("/variables")
                        .bearerAuth("test-registered-user"), String.class
        );

        response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        data = result.getAsJsonArray("data");
        assertEquals(2, data.size(), "Array size should be 2");

        Iterator<Trait> itr = programTraits.iterator();

        for (JsonElement element : data) {
            JsonObject variable = element.getAsJsonObject();
            checkTraits(itr.next(), variable);
        }

    }

    @Test
    @Order(3)
    void userInMultipleProgramsWithTraits() {

        // create trait in db and brapi service for second program
        otherProgramTraits = List.of(buildTestTrait("Test trait 3", "c"), buildTestTrait("Test trait 4", "d"));

        Flowable<HttpResponse<String>> call = biClient.exchange(
                POST("/programs/" + otherValidProgram.getId() + "/traits", otherProgramTraits)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "other-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(2, data.size(), "Array size should be 2");

        // get brapi variables and check matches
        call = client.exchange(
                GET("/variables")
                        .bearerAuth("other-registered-user"), String.class
        );

        response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        data = result.getAsJsonArray("data");
        assertEquals(4, data.size(), "Array size should be 4");

        List<Trait> allTraits = Stream.concat(programTraits.stream(), otherProgramTraits.stream())
                .collect(Collectors.toList());
        Iterator<Trait> itr = allTraits.iterator();

        for (JsonElement element : data) {
            JsonObject variable = element.getAsJsonObject();
            checkTraits(itr.next(), variable);
        }
    }

    @Test
    @Order(4)
    void getBrapiPageZero() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/variables?page=0").bearerAuth("test-registered-user"), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(4, data.size(), "Wrong number of results returned");

        JsonObject metadata = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("metadata");
        JsonObject pagination = metadata.getAsJsonObject("pagination");
        assertEquals(1, pagination.get("totalPages").getAsInt(), "Wrong total pages");
        assertEquals(0, pagination.get("currentPage").getAsInt(), "Wrong current page");
        assertEquals(4, pagination.get("pageSize").getAsInt(), "Wrong page size");
        assertEquals(4, pagination.get("totalCount").getAsInt(), "Wrong total count");

    }

    @Test
    @Order(4)
    void getBrapiPageZeroMultiplePages() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/variables?page=0&pageSize=1").bearerAuth("other-registered-user"), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(1, data.size(), "Wrong number of results returned");

        JsonObject variable = data.get(0).getAsJsonObject();
        observationVariableDbId = variable.get("observationVariableDbId").getAsString();

        JsonObject metadata = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("metadata");
        JsonObject pagination = metadata.getAsJsonObject("pagination");
        assertEquals(4, pagination.get("totalPages").getAsInt(), "Wrong total pages");
        assertEquals(0, pagination.get("currentPage").getAsInt(), "Wrong current page");
        assertEquals(1, pagination.get("pageSize").getAsInt(), "Wrong page size");
        assertEquals(4, pagination.get("totalCount").getAsInt(), "Wrong total count");

    }

    @Test
    @Order(4)
    void filterTraitClass() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/variables?traitClass=a").bearerAuth("other-registered-user"), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(1, data.size(), "Wrong number of results returned");
        JsonObject variable = data.get(0).getAsJsonObject();
        JsonObject trait = variable.getAsJsonObject("trait");
        assertEquals("a", trait.get("class").getAsString(), "Wrong trait class");
    }

    @Test
    @Order(5)
    void filterObservationVariableDbId() {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/variables?observationVariableDbId="+observationVariableDbId).bearerAuth("other-registered-user"), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.getBody().get()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(1, data.size(), "Wrong number of results returned");
        JsonObject variable = data.get(0).getAsJsonObject();
        assertEquals(observationVariableDbId, variable.get("observationVariableDbId").getAsString(), "Wrong trait class");
    }

}
