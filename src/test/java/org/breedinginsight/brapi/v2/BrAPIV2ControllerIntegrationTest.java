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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.client.v2.typeAdapters.PaginationTypeAdapter;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrAPIPagination;
import org.brapi.v2.model.core.BrAPIServerInfo;
import org.brapi.v2.model.pheno.*;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
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
            .registerTypeAdapter(BrAPIPagination.class, new PaginationTypeAdapter())
                                         .create();

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private UserDAO userDAO;

    private ProgramEntity validProgram;

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

        assertEquals("Breeding Insight", serverInfo.getOrganizationName());
        assertEquals("DeltaBreed", serverInfo.getServerName());
        assertEquals("bidevteam@cornell.edu", serverInfo.getContactEmail());
        assertEquals("https://breedinginsight.org", serverInfo.getOrganizationURL());
        assertEquals("BrAPI endpoints are not implemented at the root of this domain.  Please make BrAPI calls in the context of a program (ex: https://app.breedinginsight.net/v1/programs/{programId}/brapi/v2)", serverInfo.getServerDescription());
        assertEquals("Cornell University, Ithaca, NY, USA", serverInfo.getLocation());
        assertEquals("https://brapi.org/specification", serverInfo.getDocumentationURL());
    }

    @Test
    @SneakyThrows
    public void testPostVariablesNotFound() {
        BrAPIObservationVariable variable = generateVariable();

        Flowable<HttpResponse<String>> postCall = biClient.exchange(
                POST(String.format("%s/programs/%s/brapi/v2/variables",
                                   biApiVersion,
                                   validProgram.getId().toString()), Arrays.asList(variable))
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
    public void testPutVariablesNotFound() {
        BrAPIObservationVariable variable = generateVariable();

        Flowable<HttpResponse<String>> postCall = biClient.exchange(
                POST(String.format("%s/programs/%s/brapi/v2/variables",
                                   biApiVersion,
                                   validProgram.getId().toString()), Arrays.asList(variable))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bearerAuth("test-registered-user"), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = postCall.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }



    private BrAPIObservationVariable generateVariable() {
        var random = UUID.randomUUID()
                         .toString();
        return new BrAPIObservationVariable().observationVariableName("test" + random)
                                             .commonCropName("Grape")
                                             .externalReferences(Collections.singletonList(new BrAPIExternalReference().referenceID("abc123")
                                                                                                                       .referenceId("abc123")
                                                                                                                       .referenceSource("breedinginsight.org")))
                                             .trait(new BrAPITrait().traitClass("Agronomic")
                                                                    .traitName("test trait" + random))
                                             .method(new BrAPIMethod().methodName("test method" + random)
                                                                      .methodClass("Measurement"))
                                             .scale(new BrAPIScale().scaleName("test scale" + random)
                                                                    .dataType(BrAPITraitDataType.NUMERICAL));
    }
}
