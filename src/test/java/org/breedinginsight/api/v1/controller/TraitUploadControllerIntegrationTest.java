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
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import junit.framework.AssertionFailedError;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.api.model.v1.response.RowValidationErrors;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TraitUploadControllerIntegrationTest extends DatabaseTest {

    private FannyPack fp;

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    private ProgramEntity validProgram;
    private File validFile = new File("src/test/resources/files/data_one_row.csv");
    String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    String invalidProgram = invalidUUID;

    @BeforeAll
    public void setup() {

        // Insert test data into the db
        fp = FannyPack.fill("src/test/resources/sql/UploadControllerIntegrationTest.sql");

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert user into program
        dsl.execute(fp.get("InsertProgramUser"));

        // Insert user into program as not active
        dsl.execute(fp.get("InsertInactiveProgramUser"));

        // Insert Trait
        dsl.execute(fp.get("InsertProgramObservationLevel"));
        dsl.execute(fp.get("InsertProgramOntology"));
        dsl.execute(fp.get("InsertMethod"));
        dsl.execute(fp.get("InsertScale"));
        dsl.execute(fp.get("InsertTrait"));

        // Retrieve our new data
        validProgram = programDao.findAll().get(0);

    }

    @Test
    @Order(1)
    void putTraitUploadDuplicatesInDB() {
        // Database duplicates do not throw an error

        File file = new File("src/test/resources/files/data_one_row.csv");

        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        checkValidTraitUpload(result);

        dsl.execute(fp.get("DeleteTrait"));
    }

    @Test
    @Order(2)
    void putTraitUploadTraitLevelDoesNotExist() {

        File file = new File("src/test/resources/files/data_one_row_trait_level_not_exist.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertTrue(rowErrors.size() == 1, "Wrong number of row errors returned");

        JsonObject rowError1 = rowErrors.get(0).getAsJsonObject();
        JsonArray errors = rowError1.getAsJsonArray("errors");
        assertTrue(errors.size() == 1, "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertEquals(404, error.get("httpStatusCode").getAsInt(), "Incorrect http status code");

        dsl.execute(fp.get("DeleteTrait"));
    }

    @Test
    void putTraitUploadInvalidProgramId() {
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(invalidProgram, validFile, "test-registered-user");
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void putTraitUploadUserNotInProgram() {
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), validFile, "other-registered-user");
        });
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test
    void putTraitUploadUserInactiveInProgram() {

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), validFile, "another-registered-user");
        });
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test
    void putTraitUploadMissingMediaType() {
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("file","test", new byte[1]).build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+validProgram.getId()+"/trait-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getStatus());
    }

    @Test
    void putTraitUploadUnsupportedMimeType() {
        File file = new File("src/test/resources/files/unsupported.txt");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getStatus());
    }

    @Test
    void putTraitUploadMissingRequiredColumn() {
        File file = new File("src/test/resources/files/missing_method_name_with_data.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void putTraitUploadMissingFormula() {
        File file = new File("src/test/resources/files/missing_formula.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        // Check for conflict response
        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        checkMultiErrorResponse(rowErrors);
    }

    @Test
    void putTraitUploadMissingCategories() {
        File file = new File("src/test/resources/files/missing_categories.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        // Check for conflict response
        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        checkMultiErrorResponse(rowErrors);
    }

    @Test
    void putTraitUploadDuplicatesInFile() {

        File file = new File("src/test/resources/files/duplicatesInFile.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertTrue(rowErrors.size() == 2, "Wrong number of row errors returned");

        JsonObject rowError1 = rowErrors.get(0).getAsJsonObject();
        JsonArray errors = rowError1.getAsJsonArray("errors");
        assertTrue(errors.size() == 2, "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertEquals(409, error.get("httpStatusCode").getAsInt(), "Incorrect http status code");

        JsonObject rowError2 = rowErrors.get(0).getAsJsonObject();
        JsonArray errors2 = rowError2.getAsJsonArray("errors");
        assertTrue(errors2.size() == 2, "Not enough errors were returned");
        JsonObject error2 = errors2.get(0).getAsJsonObject();
        assertEquals(409, error2.get("httpStatusCode").getAsInt(), "Incorrect http status code");
    }

    @Test
    public void putTraitUploadEmptyRowBetweenRows() {
        // Empty rows should be ignored

        File file = new File("src/test/resources/files/empty_then_2_rows.xlsx");
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        checkValidTraitUpload(result);
    }

    @Test
    public void putTraitUploadBadTypes() {
        // Should only return parsing exceptions, not validation exceptions
        // File also contains missing trait name and bad trait level

        File file = new File("src/test/resources/files/parsing_exceptions.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(1, rowErrors.size(), "Wrong number of row errors returned");
        JsonArray rowValidationErrors = rowErrors.get(0).getAsJsonObject().get("errors").getAsJsonArray();
        assertEquals(6, rowValidationErrors.size(), "Wrong number of errors for row");
        Map<String, Integer> expectedColumns = new HashMap<>();
        expectedColumns.put("Trait status", 422);
        expectedColumns.put("Scale categories", 422);
        expectedColumns.put("Scale class", 422);
        expectedColumns.put("Scale decimal places", 422);
        expectedColumns.put("Scale lower limit", 422);
        expectedColumns.put("Scale upper limit", 422);
        List<Boolean> seenTrackList = expectedColumns.keySet().stream().map(column -> false).collect(Collectors.toList());

        Boolean unknownColumnReturned = false;
        for (JsonElement error: rowValidationErrors){
            JsonObject jsonError = (JsonObject) error;
            String column = jsonError.get("field").getAsString();
            if (expectedColumns.containsKey(column)){
                assertEquals(expectedColumns.get(column), jsonError.get("httpStatusCode").getAsInt(), "Wrong code was returned");
            } else {
                unknownColumnReturned = true;
            }
        }

        if (unknownColumnReturned){
            throw new AssertionFailedError("Unexpected error was returned");
        }
    }

    @Test
    @Order(1)
    void getTraitUploadDoesNotExist() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgram.getId()+"/trait-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void deleteTraitUploadInvalidProgram() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+invalidProgram+"/trait-upload")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    @Test
    void putTraitUploadXlsxSuccess() {
        File file = new File("src/test/resources/files/data_one_row.xlsx");
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        checkValidTraitUpload(result);
    }

    @Test
    void putTraitUploadXlsSuccess() {
        File file = new File("src/test/resources/files/data_one_row.xls");
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        checkValidTraitUpload(result);
    }

    @Test
    @Order(3)
    void putTraitUploadCsvSuccess() {

        File file = new File("src/test/resources/files/data_one_row.csv");
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        checkValidTraitUpload(result);

    }

    private HttpResponse<String> uploadFile(String programId, File file, String user) {
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("file",
                        file
                ).build();

        Flowable<HttpResponse<String>> call = client.exchange(
                PUT("/programs/"+programId+"/trait-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", user)), String.class
        );

        return call.blockingFirst();
    }

    private void checkValidTraitUpload(JsonObject traitUpload) {

        assertEquals("TRAIT", traitUpload.get("type").getAsString(), "wrong type");

        JsonArray data = traitUpload.getAsJsonArray("data");
        JsonObject trait = data.get(0).getAsJsonObject();

        JsonArray abbreviations = trait.getAsJsonArray("abbreviations");
        String abb1 = abbreviations.get(0).getAsString();
        String abb2 = abbreviations.get(1).getAsString();

        assertEquals(2, abbreviations.size(), "number of abbreviations different than expected");
        assertEquals("PMSevLeaf", abb1, "wrong abbreviation");
        assertEquals("PM_LEAF_P4", abb2, "wrong abbreviation");

        JsonArray synonyms = trait.getAsJsonArray("synonyms");
        String syn1 = synonyms.get(0).getAsString();
        String syn2 = synonyms.get(1).getAsString();

        assertEquals(2, synonyms.size(), "number of synonyms different than expected");
        assertEquals("Powdery Mildew", syn1, "wrong synonym");
        assertEquals("Powdery Mildew Severity", syn2, "wrong synonym");

        assertEquals("Powdery Mildew severity field, leaves", trait.get("traitName").getAsString(), "wrong trait name");

        JsonObject observationLevel = trait.getAsJsonObject("programObservationLevel");
        assertEquals("Plant", observationLevel.get("name").getAsString(), "wrong level name");

        assertEquals("Powdery mildew (PM) due to Erysiphe necator severity in field, leaves only", trait.get("description").getAsString(), "wrong description");

        assertEquals(true, trait.get("active").getAsBoolean(), "wrong status");
        // TODO: trait lists

        JsonObject method = trait.get("method").getAsJsonObject();
        assertEquals("Powdery Mildew severity, leaves - Estimation", method.get("methodName").getAsString(), "wrong method name");
        assertEquals("Observed severity of Powdery Mildew on leaves", method.get("description").getAsString(), "wrong method description");
        assertEquals("Estimation", method.get("methodClass").getAsString(), "wrong method class");
        assertEquals("a^2 + b^2 = c^2", method.get("formula").getAsString(), "wrong method formula");

        JsonObject scale = trait.get("scale").getAsJsonObject();
        assertEquals("1-4 Parlier field response score", scale.get("scaleName").getAsString(), "wrong scale name");
        assertEquals(DataType.ORDINAL.getLiteral(), scale.get("dataType").getAsString(), "wrong scale dataType");
        assertEquals(2, scale.get("decimalPlaces").getAsInt(), "wrong scale decimal places");
        assertEquals(2, scale.get("validValueMin").getAsInt(), "wrong scale min value");
        assertEquals(9999, scale.get("validValueMax").getAsInt(), "wrong scale max value");

        JsonObject program = traitUpload.get("program").getAsJsonObject();
        assertEquals("Test Program", program.get("name").getAsString(), "wrong program name");

        JsonObject user = traitUpload.get("user").getAsJsonObject();
        assertEquals("Test User", user.get("name").getAsString(), "wrong user name");

    }

    @Test
    @Order(4)
    void getTraitUploadSuccess() {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgram.getId()+"/trait-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        checkValidTraitUpload(result);
    }

    @Test
    @Order(5)
    void deleteTraitUploadSuccess() {
        Flowable<HttpResponse<String>> call = client.exchange(
                DELETE("/programs/"+validProgram.getId()+"/trait-upload")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    void checkMultiErrorResponse(JsonArray rowErrors) {

        assertTrue(rowErrors.size() > 0, "Wrong number of row errors returned");
        JsonObject rowError = rowErrors.get(0).getAsJsonObject();

        JsonArray errors = rowError.getAsJsonArray("errors");
        assertTrue(errors.size() > 0, "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertTrue(error.has("field"), "Column field not included in return");
        assertTrue(error.has("errorMessage"), "errorMessage field not included in return");
        assertTrue(error.has("httpStatus"), "httpStatus field not included in return");
        assertTrue(error.has("httpStatusCode"), "httpStatusCode field not included in return");
    }
}
