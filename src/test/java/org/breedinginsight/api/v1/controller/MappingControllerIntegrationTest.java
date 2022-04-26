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
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.brapps.importer.base.daos.ImportMappingDAO;
import org.breedinginsight.brapps.importer.base.model.mapping.ImportMapping;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.services.SpeciesService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.io.File;
import java.time.OffsetDateTime;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MappingControllerIntegrationTest extends BrAPITest {

    private FannyPack fp;
    private Program validProgram;
    private BiUserEntity testUser;
    private ImportMapping validMapping;

    @Inject
    private DSLContext dsl;
    @Inject
    private SpeciesService speciesService;
    @Inject
    private UserDAO userDAO;
    @Inject
    private ImportMappingDAO importMappingDAO;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @AfterAll
    public void finish() { super.stopContainers(); }

    @BeforeAll
    @SneakyThrows
    public void setup() {

        fp = FannyPack.fill("src/test/resources/sql/ImportControllerIntegrationTest.sql");
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        var brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        // Set up BrAPI
        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));

        // Insert system import
        dsl.execute(fp.get("InsertSystemImport"));

        // Species
        Species validSpecies = speciesService.getAll().get(0);
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .commonName(validSpecies.getCommonName())
                .id(validSpecies.getId())
                .build();

        // Insert program
        ProgramRequest program = ProgramRequest.builder()
                .name("Test Program")
                .species(speciesRequest)
                .key("TEST")
                .build();
        validProgram = insertAndFetchTestProgram(program);

        // Set program user
        testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), validProgram.getId());
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Get system mapping. Need two calls to get the whole mapping
        validMapping = importMappingDAO.getSystemMappingByName("germplasmtest").get(0);
        validMapping = importMappingDAO.getMapping(validMapping.getId()).get();
    }

    public Program insertAndFetchTestProgram(ProgramRequest programRequest) {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/", gson.toJson(programRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        Program program = gson.fromJson(result, Program.class);
        return program;
    }

    @Test
    @SneakyThrows
    @Order(1)
    public void createProgramMappingSuccess() {
        // Create mapping
        File file = new File("src/test/resources/files/germplasm_import_single_row.csv");
        HttpResponse<String> response = createGermplasmMapping(file, "programTestGermplasm", validMapping);
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        assertEquals("programTestGermplasm", result.getAsJsonObject().get("name").getAsString(), "Wrong import name returned");
    }

    @Test
    @SneakyThrows
    public void getSystemMappings() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        boolean testNameFound = false;
        boolean germplasmDefaultNameFound = false;
        for (JsonElement jsonMapping: data) {
            JsonObject systemMapping = (JsonObject) jsonMapping;
            if (systemMapping.get("name").getAsString().equals("GermplasmTest")){
                testNameFound = true;
            }
            if (systemMapping.get("name").getAsString().equals("GermplasmTemplateMap")){
                germplasmDefaultNameFound = true;
            }
        }
        assertTrue(testNameFound, "Desired import name 'GermplasmTest' not found");
        assertTrue(germplasmDefaultNameFound, "System import name 'GermplasmTemplateMap' not found");
    }

    @Test
    @SneakyThrows
    public void getSystemMappingByNameMisMatchesCases() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=germplasmtest").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        assertEquals(1, data.size(), "Wrong number of results returned.");
        assertEquals("GermplasmTest", data.get(0).getAsJsonObject().get("name").getAsString(), "Wrong import name returned");
    }

    @Test
    @SneakyThrows
    public void getSystemMappingByNameDoesNotExist() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=doesnotexist").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        assertEquals(0, data.size(), "Wrong number of results returned.");
    }

    @Test
    @SneakyThrows
    public void saveMappingReservedNameMisMatchCase() {
        // Create mapping
        File file = new File("src/test/resources/files/germplasm_import_single_row.csv");

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            createGermplasmMapping(file, "germplasmtest", validMapping);
        });
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    public HttpResponse<String> createGermplasmMapping(File file, String name, ImportMapping importMapping) {

        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram.getId()+"/import/mappings/file", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String mappingId = result.get("id").getAsString();
        result.addProperty("name", name);
        result.add("mapping", gson.toJsonTree(validMapping.getMappingConfig()));
        result.addProperty("importerTemplateId", validMapping.getImporterTemplateId());
        result.remove("file");

        Flowable<HttpResponse<String>> nameUpdateCall = client.exchange(
                PUT("/programs/"+validProgram.getId()+"/import/mappings/" + mappingId, gson.toJson(result))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        return nameUpdateCall.blockingFirst();
    }
}
