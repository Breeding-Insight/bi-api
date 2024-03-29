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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import junit.framework.AssertionFailedError;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.enums.TermType;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.SpeciesService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static io.micronaut.http.HttpRequest.*;
import static org.breedinginsight.TestUtils.insertAndFetchTestProgram;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TraitUploadControllerIntegrationTest extends BrAPITest {

    private FannyPack fp;

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private UserDAO userDAO;
    @Inject
    private TraitDAO traitDAO;
    @Inject
    private SpeciesService speciesService;

    private Species validSpecies;

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @Inject
    @Client("/${micronaut.bi.api.version}")
    RxHttpClient client;

    private Program validProgram;
    private File validFile = new File("src/test/resources/files/data_one_row.csv");
    private String validUploadId;
    String invalidUUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    String invalidProgram = invalidUUID;

    @BeforeAll
    public void setup() {

        // Insert test data into the db
        fp = FannyPack.fill("src/test/resources/sql/UploadControllerIntegrationTest.sql");
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        var brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));

        // Insert program
        //dsl.execute(fp.get("InsertProgram"));

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

        validProgram = insertAndFetchTestProgram(program);

        // Insert user into program
        //dsl.execute(fp.get("InsertProgramUser"));

        // Insert user into program as not active
        dsl.execute(fp.get("InsertInactiveProgramUser"));

        // Insert Trait
        dsl.execute(fp.get("InsertProgramObservationLevel"));
        //dsl.execute(fp.get("InsertProgramOntology"));
        dsl.execute(fp.get("InsertMethod"));
        dsl.execute(fp.get("InsertScale"));
        dsl.execute(fp.get("InsertTrait"));

    }

    public Species getTestSpecies() {
        List<Species> species = speciesService.getAll();
        return species.get(0);
    }

    public Program insertAndFetchTestProgram(ProgramRequest programRequest) {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/", gson.toJson(programRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String programId = result.get("id").getAsString();

        Program program = getProgramById(UUID.fromString(programId));

        return program;
    }

    public Program getProgramById(UUID programId) {

        dsl.execute(fp.get("InsertProgramUser"));

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+programId.toString()).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonObject("result");

        Program program = gson.fromJson(result, Program.class);

        return program;
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
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());

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
    void putTraitUploadUnsupportedMimeTypePdf() {
        File file = new File("src/test/resources/files/pdf_file.pdf");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getStatus());
    }

    @Test
    void putTraitUploadUnsupportedMaskedAsSupportedMimeType() {
        File file = new File("src/test/resources/files/pdf_file_masked_as_csv.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getStatus());
    }

    @Test
    void putTraitUploadMissingRequiredColumn() {
        File file = new File("src/test/resources/files/missing_trait_name_with_data.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }


    @Test
    public void putTraitUploadEmptyRowBetweenRows() {
        // Empty row in middle of file should throw an error. Our error indices will be messed up otherwise.

        File file = new File("src/test/resources/files/empty_then_2_rows.xlsx");
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
    void putTraitUploadMissingLabelsOrdinal() {
        File file = new File("src/test/resources/files/missing_category_label_ordinal.csv");
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        // Check for conflict response
        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        checkMultiErrorResponse(rowErrors);
    }

    @Test
    void putTraitUploadMissingLabelsNominal() {
        File file = new File("src/test/resources/files/missing_category_label_nominal.csv");
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
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
        assertTrue(errors.size() == 1, "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertEquals(409, error.get("httpStatusCode").getAsInt(), "Incorrect http status code");

        JsonObject rowError2 = rowErrors.get(0).getAsJsonObject();
        JsonArray errors2 = rowError2.getAsJsonArray("errors");
        assertTrue(errors2.size() == 1, "Not enough errors were returned");
        JsonObject error2 = errors2.get(0).getAsJsonObject();
        assertEquals(409, error2.get("httpStatusCode").getAsInt(), "Incorrect http status code");
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
        assertEquals(7, rowValidationErrors.size(), "Wrong number of errors for row");
        Map<String, Integer> expectedColumns = new HashMap<>();
        expectedColumns.put("Status", 422);
        expectedColumns.put("Method class", 422);
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
    void putTraitUploadSingleTraitLevelError() {
        File file = new File("src/test/resources/files/missing_trait_entity.xlsx");

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(1, rowErrors.size(), "Wrong number of row errors returned");
        JsonArray rowValidationErrors = rowErrors.get(0).getAsJsonObject().get("errors").getAsJsonArray();
        assertEquals(1, rowValidationErrors.size(), "Wrong number of errors for row");
        JsonObject error = rowValidationErrors.get(0).getAsJsonObject();
        assertTrue(error.get("field").getAsString().contains("Trait Entity"), "Wrong field returned");
        assertFalse(error.get("field").getAsString().contains("Trait Level"), "Wrong field returned");
    }

    @Test
    void putTraitUploadSingleMissingScaleClassError() {
      File file = new File("src/test/resources/files/missing_scale_class.xlsx");

      HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
          HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
      });
      assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

      JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
      assertEquals(1, rowErrors.size(), "Wrong number of row errors returned");
      JsonArray rowValidationErrors = rowErrors.get(0).getAsJsonObject().get("errors").getAsJsonArray();
      assertEquals(1, rowValidationErrors.size(), "Wrong number of errors for row");
      JsonObject error = rowValidationErrors.get(0).getAsJsonObject();
      assertTrue(error.get("field").getAsString().contains("Scale Class"), "Wrong field returned");
      assertFalse(error.get("field").getAsString().contains("Unit"), "Wrong field returned");
    }

    @Test
    void putTraitUploadSingleMissingScaleUnitError() {
        File file = new File("src/test/resources/files/missing_scale_unit.xlsx");

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertEquals(1, rowErrors.size(), "Wrong number of row errors returned");
        JsonArray rowValidationErrors = rowErrors.get(0).getAsJsonObject().get("errors").getAsJsonArray();
        assertEquals(1, rowValidationErrors.size(), "Wrong number of errors for row");
        JsonObject error = rowValidationErrors.get(0).getAsJsonObject();
        assertTrue(error.get("field").getAsString().contains("Unit"), "Wrong field returned");
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

    @Test
    void postTraitComputation() {
        // Check that the scale class is changed to numeric
        File file = new File("src/test/resources/files/data_one_row_computation.csv");
        HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        JsonArray data = result.getAsJsonArray("data");
        JsonObject trait = data.get(0).getAsJsonObject();
        JsonObject scale = trait.get("scale").getAsJsonObject();
        assertEquals(DataType.NUMERICAL.getLiteral(), scale.get("dataType").getAsString(), "wrong scale dataType");
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

        JsonArray synonyms = trait.getAsJsonArray("synonyms");
        String syn1 = synonyms.get(0).getAsString();
        String syn2 = synonyms.get(1).getAsString();

        assertEquals(3, synonyms.size(), "number of synonyms different than expected");
        assertEquals("Powdery Mildew", syn1, "wrong synonym");
        assertEquals("Powdery Mildew Severity", syn2, "wrong synonym");

        assertEquals("PM_Leaf", trait.get("observationVariableName").getAsString(), "wrong trait name");

        JsonObject observationLevel = trait.getAsJsonObject("programObservationLevel");
        assertEquals("Leaf", observationLevel.get("name").getAsString(), "wrong level name");

        assertEquals(true, trait.get("active").getAsBoolean(), "wrong status");
        assertEquals(TermType.GERM_PASSPORT.toString(), trait.get("termType").getAsString());

        // TODO: trait lists

        JsonObject method = trait.get("method").getAsJsonObject();
        assertEquals("Powdery Mildew severity, leaf", method.get("description").getAsString(), "wrong method description");
        assertEquals("Estimation", method.get("methodClass").getAsString(), "wrong method class");
        assertEquals("a^2 + b^2 = c^2", method.get("formula").getAsString(), "wrong method formula");

        JsonObject scale = trait.get("scale").getAsJsonObject();
        assertEquals("Ordinal", scale.get("scaleName").getAsString(), "wrong scale name");
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

    @Test
    @Order(6)
    void getTraitUploadQuery() {

        File file = new File("src/test/resources/files/data_multiple_rows.csv");

        HttpResponse<String> uploadResponse = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, uploadResponse.getStatus());

        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/programs/"+validProgram.getId()+"/trait-upload?page=1&pageSize=2&sortField=name&sortOrder=DESC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(2, data.size(), "Wrong number of results returned");
        TestUtils.checkStringSorting(data, "observationVariableName", SortOrder.DESC);

        JsonObject metadata = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        JsonObject pagination = metadata.getAsJsonObject("pagination");
        assertEquals(2, pagination.get("totalPages").getAsInt(), "Wrong number of pages");
        assertEquals(3, pagination.get("totalCount").getAsInt(), "Wrong total count");
        assertEquals(1, pagination.get("currentPage").getAsInt(), "Wrong current page");
    }

    @Test
    @Order(7)
    void searchTraitUpload() {

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilters(new ArrayList<>());
        searchRequest.getFilters().add(new FilterRequest("name", "leaf"));

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram.getId()+"/trait-upload/search?page=1&pageSize=2&sortField=name&sortOrder=DESC", searchRequest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(1, data.size(), "Wrong number of results returned");
        TestUtils.checkStringSorting(data, "observationVariableName", SortOrder.DESC);

        JsonObject metadata = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("metadata");
        JsonObject pagination = metadata.getAsJsonObject("pagination");
        assertEquals(1, pagination.get("totalPages").getAsInt(), "Wrong number of pages");
        assertEquals(1, pagination.get("totalCount").getAsInt(), "Wrong total count");
        assertEquals(1, pagination.get("currentPage").getAsInt(), "Wrong current page");

        this.validUploadId = result.get("id").getAsString();
    }


    @Test
    @Order(8)
    void postTraitUpload() {
        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram.getId()+"/trait-upload/" + this.validUploadId, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        List<Trait> traits = traitDAO.getTraitsByProgramId(validProgram.getId());
        assertEquals(3, traits.size(), "Wrong number of traits inserted");
    }

    @Test
    @Order(9)
    void postTraitUploadDuplicateTraits() {
        // No traits should be inserted by the end of this
        File file = new File("src/test/resources/files/data_multiple_rows.csv");

        HttpResponse<String> uploadResponse = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, uploadResponse.getStatus());
        JsonObject result = JsonParser.parseString(uploadResponse.body()).getAsJsonObject().getAsJsonObject("result");
        this.validUploadId = result.get("id").getAsString();

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/"+validProgram.getId()+"/trait-upload/" + this.validUploadId, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        List<Trait> traits = traitDAO.getTraitsByProgramId(validProgram.getId());
        assertEquals(3, traits.size(), "Wrong number of traits inserted");
    }

    @Test
    @Order(10)
    void putTraitUploadMismatchedCases() {

        // Traits should be inserted just fine
        File file = new File("src/test/resources/files/data_mismatched_cases.csv");

        HttpResponse<String> uploadResponse = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        assertEquals(HttpStatus.OK, uploadResponse.getStatus());
        JsonObject result = JsonParser.parseString(uploadResponse.body()).getAsJsonObject().getAsJsonObject("result");

        checkValidTraitUpload(result);
    }

    @Test
    @Order(10)
    void putTraitUploadDuplicateMismatchedHeaders() {

        File file = new File("src/test/resources/files/data_duplicate_headers.csv");

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    @Order(10)
    void putTraitUploadMinMaxSwap() {

        // Traits should be inserted just fine
        File file = new File("src/test/resources/files/data_min_max_swap.csv");

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
    }

    @Test
    void putNominalMissingCategories() {

        File file = new File("src/test/resources/files/ontology/data_nominal_missing_categories.xlsx");

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertTrue(rowErrors.size() == 4, "Wrong number of row errors returned");

        JsonObject rowError1 = rowErrors.get(0).getAsJsonObject();
        JsonArray errors = rowError1.getAsJsonArray("errors");
        assertTrue(errors.size() == 1, "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertEquals(422, error.get("httpStatusCode").getAsInt(), "Incorrect http status code");
        assertTrue(error.get("errorMessage").getAsString().contains("Nominal"), "Incorrect http status code");
    }

    @Test
    void putNominalCategoryLabelPresentError() {

        File file = new File("src/test/resources/files/ontology/data_category_label_nominal_trait.xls");

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
        assertEquals(422, error.get("httpStatusCode").getAsInt(), "Incorrect http status code");
        assertEquals("Scale categories contain errors", error.get("errorMessage").getAsString(), "Incorrect http status code");

        // Check specific error
        JsonArray categoryErrors = error.get("rowErrors").getAsJsonArray();
        assertEquals(3, categoryErrors.size(), "Wrong number of category row errors");
        JsonArray categoryRowErrors = categoryErrors.get(0).getAsJsonObject().get("errors").getAsJsonArray();
        assertEquals(1, categoryRowErrors.size(), "Wrong number of category errors");
        JsonObject labelError = categoryRowErrors.get(0).getAsJsonObject();
        assertEquals("Scale label cannot be populated for Nominal scale type", labelError.get("errorMessage").getAsString());
    }

    @Test
    void putNonNumericalClassWithUnitError() {

        File file = new File("src/test/resources/files/ontology/non_numerical_with_unit.xlsx");

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
        assertEquals(422, error.get("httpStatusCode").getAsInt(), "Incorrect http status code");
        assertEquals("Units identified for non-numeric scale class", error.get("errorMessage").getAsString(), "Incorrect http status code");
    }

    @Test
    void putOrdinalMissingCategories() {

        File file = new File("src/test/resources/files/ontology/data_ordinal_missing_categories.xlsx");

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = uploadFile(validProgram.getId().toString(), file, "test-registered-user");
        });
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());

        JsonArray rowErrors = JsonParser.parseString((String) e.getResponse().getBody().get()).getAsJsonObject().getAsJsonArray("rowErrors");
        assertTrue(rowErrors.size() == 4, "Wrong number of row errors returned");

        JsonObject rowError1 = rowErrors.get(0).getAsJsonObject();
        JsonArray errors = rowError1.getAsJsonArray("errors");
        assertTrue(errors.size() == 1, "Not enough errors were returned");
        JsonObject error = errors.get(0).getAsJsonObject();
        assertEquals(422, error.get("httpStatusCode").getAsInt(), "Incorrect http status code");
        assertTrue(error.get("errorMessage").getAsString().contains("Ordinal"), "Incorrect http status code");
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
