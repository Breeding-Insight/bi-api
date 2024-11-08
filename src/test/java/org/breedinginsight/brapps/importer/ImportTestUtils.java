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

package org.breedinginsight.brapps.importer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.SpeciesService;
import org.breedinginsight.services.writers.CSVWriter;
import org.jooq.DSLContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.micronaut.http.HttpRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Intended to be a utility class, but methods being static was causing issues.
 *
 * To use, instantiate a new instance of this class, then use it like a regular static utility class
 */
@Slf4j
public class ImportTestUtils {

    Pattern UUID_REGEX = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public Program insertAndFetchTestProgram(ProgramRequest programRequest, RxHttpClient client, Gson gson) {

        Flowable<HttpResponse<String>> call = client.exchange(
                POST("/programs/", gson.toJson(programRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpResponse<String> response = call.blockingFirst();
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        return gson.fromJson(result, Program.class);
    }
    public Flowable<HttpResponse<String>> uploadDataFile(File file, Map<String, String> userData, Boolean commit, RxHttpClient client, Program program, String mappingId) {
        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", program.getId(), mappingId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String importId = result.get("importId").getAsString();

        // Process data
        String url = String.format("/programs/%s/import/mappings/%s/data/%s/%s", program.getId(), mappingId, importId, commit ? "commit" : "preview");
        Flowable<HttpResponse<String>> processCall = client.exchange(
                PUT(url, userData)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        return processCall;
    }

    public Flowable<HttpResponse<String>> uploadWorkflowDataFile(File file,
                                                                 Map<String, String> userData,
                                                                 Boolean commit,
                                                                 RxHttpClient client,
                                                                 Program program,
                                                                 String mappingId,
                                                                 String workflowId) {

        MultipartBody requestBody = MultipartBody.builder().addPart("file", file).build();

        // Upload file
        String uploadUrl = String.format("/programs/%s/import/mappings/%s/data", program.getId(), mappingId);
        Flowable<HttpResponse<String>> call = client.exchange(
                POST(uploadUrl, requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String importId = result.get("importId").getAsString();

        // Process data
        String url = String.format("/programs/%s/import/mappings/%s/workflows/%s/data/%s/%s", program.getId(), mappingId, workflowId, importId, commit ? "commit" : "preview");
        Flowable<HttpResponse<String>> processCall = client.exchange(
                PUT(url, userData)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        return processCall;

    }

    public HttpResponse<String> getUploadedFile(String importId, RxHttpClient client, Program program, String mappingId) throws InterruptedException {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/import/mappings/%s/data/%s?mapping=true", program.getId(), mappingId, importId))
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();

        if (response.getStatus().equals(HttpStatus.ACCEPTED)) {
            Thread.sleep(1000);
            log.debug("202 Accepted response. Sleeping for 1000ms.");
            return getUploadedFile(importId, client, program, mappingId);
        } else {
            return response;
        }
    }

    public Map<String, Object> setup(RxHttpClient client, Gson gson, DSLContext dsl, SpeciesService speciesService, UserDAO userDAO, DSLContext brapiDsl, String mappingTemplateName) {
        var securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        var brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        // Set up BrAPI
        brapiDsl.execute(brapiFp.get("InsertSpecies"));

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
        Program validProgram = this.insertAndFetchTestProgram(program, client, gson);

        // Get import
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName="+mappingTemplateName).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        String mappingId = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        BiUserEntity testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).get();
        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), validProgram.getId());
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        return Map.of("program", validProgram,
                "mappingId", mappingId,
                "testUser", testUser,
                "securityFp", securityFp);
    }

    /**
     * TODO: assumes new workflow is first in list, doesn't look at position property, would be more robust to
     * look at that instead of assuming order
     * @return
     */
    public String getExperimentWorkflowId(RxHttpClient client, int workflowIndex) {
        // Get the mapping id for experiment imports
        Flowable<HttpResponse<String>> mappingCall = client.exchange(
                GET("/import/mappings?importName=ExperimentsTemplateMap").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class);
        HttpResponse<String> mappingResponse = mappingCall.blockingFirst();
        String mappingId = JsonParser.parseString(mappingResponse.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        // Get the workflow id for the workflow at position workflowIndex in the collection of all available experiment workflows
        // GET /import/mappings{?importName}
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings/"+mappingId+"/workflows").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");

        return JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(workflowIndex).getAsJsonObject().get("id").getAsString();
    }

    public JsonObject uploadAndFetch(File file, Map<String, String> userData, Boolean commit, RxHttpClient client, Program program, String mappingId) throws InterruptedException {
        Flowable<HttpResponse<String>> call = uploadDataFile(file, userData, commit, client, program, mappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());

        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId, client, program, mappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + result);
        return result;
    }

    public JsonObject uploadAndFetchWorkflow(File file,
                                             Map<String, String> userData,
                                             Boolean commit,
                                             RxHttpClient client,
                                             Program program,
                                             String mappingId,
                                             String workflowId) throws InterruptedException {
        Flowable<HttpResponse<String>> call = uploadWorkflowDataFile(file, userData, commit, client, program, mappingId, workflowId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());

        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId, client, program, mappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + result);
        return result;
    }

    public JsonObject uploadAndFetchWorkflowNoStatusCheck(File file,
                                             Map<String, String> userData,
                                             Boolean commit,
                                             RxHttpClient client,
                                             Program program,
                                             String mappingId,
                                             String workflowId) throws InterruptedException {
        Flowable<HttpResponse<String>> call = uploadWorkflowDataFile(file, userData, commit, client, program, mappingId, workflowId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());

        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = getUploadedFile(importId, client, program, mappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        return result;
    }

    public List<Trait> createTraits(int numToCreate) {
        List<Trait> traits = new ArrayList<>();
        for (int i = 0; i < numToCreate; i++) {
            String varName = "tt_test_" + (i + 1);
            traits.add(Trait.builder()
                    .observationVariableName(varName)
                    .entity("Plant " + i)
                    .attribute("height " + i)
                    .traitDescription("test")
                    .programObservationLevel(ProgramObservationLevel.builder().name("Plot").build())
                    .scale(Scale.builder()
                            .scaleName("test scale")
                            .units("test unit")
                            .dataType(DataType.NUMERICAL)
                            .validValueMin(0)
                            .validValueMax(100)
                            .build())
                    .method(Method.builder()
                            .description("test method")
                            .methodClass("test method")
                            .build())
                    .build());
        }

        return traits;
    }

    public File writeExperimentDataToFile(List<Map<String, Object>> data, List<Trait> traits) throws IOException {
        File file = File.createTempFile("test", ".csv");

        List<Column> columns = new ArrayList<>();
        columns.add(Column.builder().value(ExperimentObservation.Columns.GERMPLASM_NAME).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.GERMPLASM_GID).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.TEST_CHECK).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.EXP_TITLE).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.EXP_DESCRIPTION).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.EXP_UNIT).dataType(Column.ColumnDataType.STRING).build());
        //columns.add(Column.builder().value(ExperimentObservation.Columns.SUB_OBS_UNIT).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.EXP_TYPE).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.ENV).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.ENV_LOCATION).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.ENV_YEAR).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.EXP_UNIT_ID).dataType(Column.ColumnDataType.STRING).build());
        //columns.add(Column.builder().value(ExperimentObservation.Columns.SUB_UNIT_ID).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.REP_NUM).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.BLOCK_NUM).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.ROW).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.COLUMN).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.LAT).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.LONG).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.ELEVATION).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.RTK).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.TREATMENT_FACTORS).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(ExperimentObservation.Columns.OBS_UNIT_ID).dataType(Column.ColumnDataType.STRING).build());

        if(traits != null) {
            traits.forEach(trait -> {
                columns.add(Column.builder().value(trait.getObservationVariableName()).dataType(Column.ColumnDataType.STRING).build());
            });
        }

        ByteArrayOutputStream byteArrayOutputStream = CSVWriter.writeToCSV(columns, data);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(byteArrayOutputStream.toByteArray());

        return file;
    }
}
