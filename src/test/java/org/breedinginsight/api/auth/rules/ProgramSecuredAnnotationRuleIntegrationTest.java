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

package org.breedinginsight.api.auth.rules;

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
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.services.SpeciesService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.breedinginsight.TestUtils.insertAndFetchTestProgram;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProgramSecuredAnnotationRuleIntegrationTest extends BrAPITest {

    private FannyPack fp;
    private FannyPack brapiFp;
    private BiUserEntity testUser;
    private BiUserEntity otherTestUser;
    private List<Program> programs;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;
    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDAO programDAO;
    @Inject
    private UserDAO userDAO;
    @Inject
    private SpeciesService speciesService;

    private Species validSpecies;

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @BeforeAll
    void setup() {
        fp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));

        validSpecies = getTestSpecies();

        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .commonName(validSpecies.getCommonName())
                .id(validSpecies.getId())
                .build();

        ProgramRequest program = ProgramRequest.builder()
                .name("Test Program")
                .species(speciesRequest)
                .key("TEST")
                .build();

        insertTestProgram(program);

        dsl.execute(fp.get("InsertPrograms"));
        programs = programDAO.getAll();
        testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        otherTestUser = userDAO.getUserByOrcId(TestTokenValidator.OTHER_TEST_USER_ORCID).get();

        // Insert system roles
        dsl.execute(fp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Insert program roles
        dsl.execute(fp.get("InsertProgramRolesBreeder"), otherTestUser.getId().toString(), programs.get(0).getId());
        dsl.execute(fp.get("InsertProgramRolesBreeder"), otherTestUser.getId().toString(), programs.get(1).getId());

    }

    public Species getTestSpecies() {
        List<Species> species = speciesService.getAll();
        return species.get(0);
    }

    public void insertTestProgram(ProgramRequest programRequest) {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/", gson.toJson(programRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
    }

    @Test
    public void programRoleMatch() {
        // User has role required
        // Returns all program in system for admin
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(programs.get(0).getSpecies().getId())
                .build();

        ProgramRequest validRequest = ProgramRequest.builder()
                .name("Another test program")
                .species(speciesRequest)
                .key("TESTA")
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs", gson.toJson(validRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    @Test
    public void programRoleGroupMatch() {
        // User has role required in program role group
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + programs.get(0).getId().toString())
                        .cookie(new NettyCookie("phylo-token", "other-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        assertEquals(programs.get(0).getId().toString(), result.get("id").getAsString(), "Response not returned");
    }

    @Test
    public void programRoleMissing() {
        // User missing role required
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .id(programs.get(0).getSpecies().getId())
                .build();

        ProgramRequest validRequest = ProgramRequest.builder()
                .name("Another test program")
                .species(speciesRequest)
                .key("TESTA")
                .build();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs", gson.toJson(validRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "other-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test
    public void programRoleGroupMissing() {
        // User missing role required in program role group
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/" + programs.get(3).getId().toString())
                        .cookie(new NettyCookie("phylo-token", "other-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test
    public void getAllProgramsAdmin() {

        // Returns all program in system for admin
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertTrue(result.size() >= 1, "Wrong number of programs");

        JsonArray data = result.getAsJsonArray("data");
        assertEquals(5, data.size(), "Wrong number of programs returned");
    }

    @Test
    public void getAllProgramsMember() {
        // Returns programs user is a part of
        // Returns all program in system for admin
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs").cookie(new NettyCookie("phylo-token", "other-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertTrue(result.size() >= 1, "Wrong number of programs");

        JsonArray data = result.getAsJsonArray("data");
        assertEquals(2, data.size(), "Wrong number of programs returned");
    }
}
