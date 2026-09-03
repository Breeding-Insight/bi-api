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
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.geno.BrAPISample;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.ImportTestUtils;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.imports.sample.SampleSubmissionImport.Columns;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.GenotypeImportDAO;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.utilities.FileUtil;
import org.breedinginsight.utilities.Utilities;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import java.io.*;
import java.util.*;

import static io.micronaut.http.HttpRequest.*;
import static org.breedinginsight.api.v1.controller.geno.SampleSubmissionController.DELETE_GENOTYPE_DATA_NOT_ALLOWED_ERROR_MESSAGE;
import static org.breedinginsight.api.v1.controller.geno.SampleSubmissionController.DELETE_STATUS_NOT_ALLOWED_ERROR_MESSAGE;
import static org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields.SUBMISSION_NAME;
import static org.breedinginsight.dao.db.Tables.GENOTYPE_IMPORT;
import static org.breedinginsight.dao.db.Tables.IMPORTER_IMPORT;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SampleSubmissionControllerIntegrationTest extends BrAPITest {

    private Program program;
    private User testUser;
    private ImportTestUtils importTestUtils;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;
    @Inject
    private DSLContext dsl;
    @Inject
    private UserDAO userDAO;
    @Inject
    private SpeciesDAO speciesDAO;
    @Inject
    private OntologyService ontologyService;
    @Inject
    private BrAPIGermplasmDAO germplasmDAO;
    @Inject
    private GenotypeImportDAO genotypeImportDAO;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    private String newExperimentWorkflowId;
    private final Gson gson = new BrAPIClient().getJSON().getGson();

    @BeforeAll
    void setup() throws Exception {
        importTestUtils = new ImportTestUtils();
        newExperimentWorkflowId = importTestUtils.getExperimentWorkflowId(client, 0);
        FannyPack fp = FannyPack.fill("src/test/resources/sql/ImportControllerIntegrationTest.sql");
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        FannyPack brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        // Test User
        testUser = userDAO.getUserByOAuthId(TestTokenValidator.TEST_USER_ORCID).orElseThrow(Exception::new);
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        // Species
        super.getBrapiDsl().execute(brapiFp.get("InsertSpecies"));
        SpeciesEntity validSpecies = speciesDAO.findAll().get(0);
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                .commonName(validSpecies.getCommonName())
                .id(validSpecies.getId())
                .build();

        // Test Program
        ProgramRequest programRequest = ProgramRequest.builder()
                .name("Test Program")
                .abbreviation("Test")
                .documentationUrl("localhost:8080")
                .objective("To test things")
                .species(speciesRequest)
                .key("TEST")
                .build();
        program = TestUtils.insertAndFetchTestProgram(gson, client, programRequest);

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), program.getId());
        dsl.execute(securityFp.get("InsertSystemRoleAdmin"), testUser.getId().toString());

        List<BrAPIGermplasm> germplasm = createGermplasm(96);
        BrAPIExternalReference newReference = new BrAPIExternalReference();
        newReference.setReferenceSource(String.format("%s/programs", BRAPI_REFERENCE_SOURCE));
        newReference.setReferenceID(program.getId().toString());

        germplasm.forEach(germ -> germ.getExternalReferences().add(newReference));

        germplasmDAO.createBrAPIGermplasm(germplasm, program.getId(), null);
    }

    @NotNull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = super.getProperties();

        properties.put("brapi.vendor-submission-enabled", "true");

        Integer containerPort = getBrapiContainer().getMappedPort(8080);
        String containerIp = getBrapiContainer().getContainerIpAddress();
        properties.put("brapi.vendors.dart.url", String.format("http://%s:%s/", containerIp, containerPort));

        return properties;
    }

    /*
    Tests
    - fetch all sample submissions
    - fetch individual sample submission
    - manual update submission status
    - brapi submit
    - check vendor status

     */

    @Test
    public void testFetchProgramSubmissions() throws IOException, InterruptedException {
        List<UUID> submissionIds = new ArrayList<>();
        submissionIds.add(createSubmission(program).getLeft().getId());
        submissionIds.add(createSubmission(program).getLeft().getId());

        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/submissions", program.getId())).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        List<SampleSubmission> submissions = gson.fromJson(JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("data"), new TypeToken<List<SampleSubmission>>(){}.getType());
        assertTrue(submissions.size() >= 2);


        List<UUID> returnedSubmissionIds = new ArrayList<>();
        for(var submission : submissions) {
            if(submissionIds.contains(submission.getId())) {
                returnedSubmissionIds.add(submission.getId());
            }
        }
        assertEquals(submissionIds.size(), returnedSubmissionIds.size());
    }

    @Test
    public void testFetchIndividualSubmissions() throws IOException, InterruptedException {
        Pair<SampleSubmission, List<Map<String, Object>>> uploadedSubmission = createSubmission(program);

        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/submissions/%s?details=true", program.getId(), uploadedSubmission.getLeft().getId())).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        SampleSubmission retrievedSubmission = gson.fromJson(JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result"), SampleSubmission.class);

        assertNotNull(retrievedSubmission);
        assertEquals(uploadedSubmission.getLeft().getId(), retrievedSubmission.getId());
        assertEquals(uploadedSubmission.getLeft().getName(), retrievedSubmission.getName());
        assertEquals(1, retrievedSubmission.getPlates().size());
        assertEquals(96, retrievedSubmission.getSamples().size());
    }

    @Test
    public void testManualUpdateSubmissions() throws IOException, InterruptedException {
        Pair<SampleSubmission, List<Map<String, Object>>> uploadedSubmission = createSubmission(program);

        Flowable<HttpResponse<String>> putCall = client.exchange(
                PUT(String.format("/programs/%s/submissions/%s/status", program.getId(), uploadedSubmission.getLeft().getId()), "{status:\"SUBMITTED\"}").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> putResponse = putCall.blockingFirst();
        assertNotNull(putResponse.body());

        Flowable<HttpResponse<String>> fetchCall = client.exchange(
                GET(String.format("/programs/%s/submissions/%s?details=true", program.getId(), uploadedSubmission.getLeft().getId())).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> fetchResponse = fetchCall.blockingFirst();
        SampleSubmission retrievedSubmission = gson.fromJson(JsonParser.parseString(fetchResponse.body()).getAsJsonObject().getAsJsonObject("result"), SampleSubmission.class);

        assertNotNull(retrievedSubmission);
        assertEquals("SUBMITTED", retrievedSubmission.getVendorStatus());
        assertNull(retrievedSubmission.getVendorOrderId());
        assertNull(retrievedSubmission.getVendorStatusLastCheck());
    }

    @Test
    public void testSubmitViaBrAPI() throws IOException, InterruptedException {
        Pair<SampleSubmission, List<Map<String, Object>>> uploadedSubmission = createSubmission(program);

        Flowable<HttpResponse<String>> postCall = client.exchange(
                POST(String.format("/programs/%s/submissions/%s/submit?vendor=dart", program.getId(), uploadedSubmission.getLeft().getId()), null).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> postResponse = postCall.blockingFirst();
        assertNotNull(postResponse.body());

        Flowable<HttpResponse<String>> fetchCall = client.exchange(
                GET(String.format("/programs/%s/submissions/%s?details=true", program.getId(), uploadedSubmission.getLeft().getId())).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> fetchResponse = fetchCall.blockingFirst();
        SampleSubmission retrievedSubmission = gson.fromJson(JsonParser.parseString(fetchResponse.body()).getAsJsonObject().getAsJsonObject("result"), SampleSubmission.class);

        assertNotNull(retrievedSubmission);
        assertEquals("SUBMITTED", retrievedSubmission.getVendorStatus());
        assertNotNull(retrievedSubmission.getVendorOrderId());
        assertNull(retrievedSubmission.getVendorStatusLastCheck());
    }

    @Test
    public void testCheckVendorStatus() throws IOException, InterruptedException {
        Pair<SampleSubmission, List<Map<String, Object>>> uploadedSubmission = createSubmission(program);

        Flowable<HttpResponse<String>> postCall = client.exchange(
                POST(String.format("/programs/%s/submissions/%s/submit?vendor=dart", program.getId(), uploadedSubmission.getLeft().getId()), null).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> postResponse = postCall.blockingFirst();
        assertNotNull(postResponse.body());

        Flowable<HttpResponse<String>> fetchCall = client.exchange(
                GET(String.format("/programs/%s/submissions/%s?details=false", program.getId(), uploadedSubmission.getLeft().getId())).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> fetchResponse = fetchCall.blockingFirst();
        SampleSubmission retrievedSubmission = gson.fromJson(JsonParser.parseString(fetchResponse.body()).getAsJsonObject().getAsJsonObject("result"), SampleSubmission.class);

        assertNotNull(retrievedSubmission);
        assertNotNull(retrievedSubmission.getVendorOrderId());
        assertNull(retrievedSubmission.getVendorStatusLastCheck());

        Flowable<HttpResponse<String>> fetchStatus = client.exchange(
                GET(String.format("/programs/%s/submissions/%s/status", program.getId(), uploadedSubmission.getLeft().getId())).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> fetchStatusResponse = fetchStatus.blockingFirst();
        assertNotNull(fetchStatusResponse.body());

        Flowable<HttpResponse<String>> fetchSubmissionAfterStatus = client.exchange(
                GET(String.format("/programs/%s/submissions/%s?details=false", program.getId(), uploadedSubmission.getLeft().getId())).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> fetchSubmissionAfterStatusResponse = fetchSubmissionAfterStatus.blockingFirst();
        SampleSubmission updatedStatusResponse = gson.fromJson(JsonParser.parseString(fetchSubmissionAfterStatusResponse.body()).getAsJsonObject().getAsJsonObject("result"), SampleSubmission.class);
        assertNotNull(updatedStatusResponse.getVendorStatusLastCheck());

        Thread.sleep(1000);

        fetchStatus = client.exchange(
                GET(String.format("/programs/%s/submissions/%s/status", program.getId(), uploadedSubmission.getLeft().getId())).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        fetchStatusResponse = fetchStatus.blockingFirst();
        assertNotNull(fetchStatusResponse.body());

        fetchSubmissionAfterStatus = client.exchange(
                GET(String.format("/programs/%s/submissions/%s?details=false", program.getId(), uploadedSubmission.getLeft().getId())).cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        fetchSubmissionAfterStatusResponse = fetchSubmissionAfterStatus.blockingFirst();
        SampleSubmission updatedStatusResponse2 = gson.fromJson(JsonParser.parseString(fetchSubmissionAfterStatusResponse.body()).getAsJsonObject().getAsJsonObject("result"), SampleSubmission.class);
        assertTrue(updatedStatusResponse2.getVendorStatusLastCheck().isAfter(updatedStatusResponse.getVendorStatusLastCheck()));
    }

    @Test
    public void testGenerateDArTFile() throws IOException, InterruptedException, ParsingException {
        Pair<SampleSubmission, List<Map<String, Object>>> uploadedSubmission = createSubmission(program);

        Flowable<HttpResponse<byte[]>> call = client.exchange(
                GET(String.format("/programs/%s/submissions/%s/dart",
                        program.getId().toString(), uploadedSubmission.getLeft().getId()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), byte[].class
        );
        HttpResponse<byte[]> response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        ByteArrayInputStream bodyStream = new ByteArrayInputStream(Objects.requireNonNull(response.body()));
        Table lookupTable = FileUtil.parseTableFromCsv(bodyStream);
        assertEquals(8, lookupTable.columnCount());
        assertEquals(Columns.PLATE_ID, lookupTable.column(0).name());
        assertEquals(Columns.ROW, lookupTable.column(1).name());
        assertEquals(Columns.COLUMN, lookupTable.column(2).name());
        assertEquals(Columns.ORGANISM, lookupTable.column(3).name());
        assertEquals(Columns.SPECIES, lookupTable.column(4).name());
        assertEquals("Genotype", lookupTable.column(5).name());
        assertEquals(Columns.TISSUE, lookupTable.column(6).name());
        assertEquals(Columns.COMMENTS, lookupTable.column(7).name());

        //Check sorting
        assertEquals("valid_1", lookupTable.column(0).get(1));
        assertEquals("B", lookupTable.column(1).get(1));
        assertEquals(0, lookupTable.column(2).get(1));
    }

    @Test
    public void testExportSampleSubmission() throws IOException, InterruptedException, ParsingException {
        Pair<SampleSubmission, List<Map<String, Object>>> uploadedSubmission = createSubmission(program);

        Flowable<HttpResponse<byte[]>> call = client.exchange(
                GET(String.format("/programs/%s/submissions/%s/export",
                        program.getId().toString(), uploadedSubmission.getLeft().getId()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), byte[].class
        );
        HttpResponse<byte[]> response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        ByteArrayInputStream bodyStream = new ByteArrayInputStream(Objects.requireNonNull(response.body()));
        Table lookupTable = FileUtil.parseTableFromCsv(bodyStream);
        assertEquals(11, lookupTable.columnCount());

        //Check columns correct
        assertEquals(Columns.GERMPLASM_NAME, lookupTable.column(0).name());
        assertEquals(Columns.GERMPLASM_GID, lookupTable.column(1).name());
        assertEquals(Columns.OBS_UNIT_ID, lookupTable.column(2).name());
        assertEquals("Sample Name", lookupTable.column(3).name());
        assertEquals(Columns.PLATE_ID, lookupTable.column(4).name());
        assertEquals(Columns.ROW, lookupTable.column(5).name());
        assertEquals(Columns.COLUMN, lookupTable.column(6).name());
        assertEquals(Columns.ORGANISM, lookupTable.column(7).name());
        assertEquals(Columns.SPECIES, lookupTable.column(8).name());
        assertEquals(Columns.TISSUE, lookupTable.column(9).name());
        assertEquals(Columns.COMMENTS, lookupTable.column(10).name());

        //Check sorting
        assertEquals(2, lookupTable.column(1).get(1));
        assertEquals("valid_1", lookupTable.column(4).get(1));
        assertEquals("B", lookupTable.column(5).get(1));
        assertEquals(0, lookupTable.column(6).get(1));
    }

    @Test
    public void testGenerateLookupFile() throws IOException, InterruptedException, ParsingException {
        Pair<SampleSubmission, List<Map<String, Object>>> uploadedSubmission = createSubmission(program);

        Flowable<HttpResponse<byte[]>> call = client.exchange(
                GET(String.format("/programs/%s/submissions/%s/lookup",
                        program.getId().toString(), uploadedSubmission.getLeft().getId()))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), byte[].class
        );
        HttpResponse<byte[]> response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        ByteArrayInputStream bodyStream = new ByteArrayInputStream(Objects.requireNonNull(response.body()));
        Table lookupTable = FileUtil.parseTableFromCsv(bodyStream);
        assertEquals(3, lookupTable.columnCount());
        assertEquals("Genotype", lookupTable.column(0).name());
        assertEquals("Germplasm Name", lookupTable.column(1).name());
        assertEquals("GID", lookupTable.column(2).name());
    }

    @Test
    public void testDeleteSubmissionEnforcesStatusAndGenotypeImportRules() throws IOException, InterruptedException {
        SampleSubmission submission = createSubmission(program).getLeft();
        String submissionUrl = String.format("/programs/%s/submissions/%s", program.getId(), submission.getId());
        NettyCookie authCookie = new NettyCookie("phylo-token", "test-registered-user");

        client.exchange(
                PUT(submissionUrl + "/status", "{status:\"SUBMITTED\"}").cookie(authCookie),
                String.class
        ).blockingFirst();

        HttpClientResponseException statusError = assertThrows(HttpClientResponseException.class, () -> client.exchange(
                DELETE(submissionUrl).cookie(authCookie), String.class
        ).blockingFirst());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, statusError.getStatus());
        assertEquals(
                DELETE_STATUS_NOT_ALLOWED_ERROR_MESSAGE,
                statusError.getResponse().getBody(String.class).orElse(null)
        );

        client.exchange(
                PUT(submissionUrl + "/status", "{status:\"NOT SUBMITTED\"}").cookie(authCookie),
                String.class
        ).blockingFirst();

        UUID importerImportId = dsl.select(IMPORTER_IMPORT.ID)
                .from(IMPORTER_IMPORT)
                .where(IMPORTER_IMPORT.PROGRAM_ID.eq(program.getId()))
                .orderBy(IMPORTER_IMPORT.CREATED_AT.desc())
                .limit(1)
                .fetchOne(IMPORTER_IMPORT.ID);
        genotypeImportDAO.createGenotypeImportLink(submission.getId(), importerImportId, testUser.getId());

        HttpClientResponseException genotypeError = assertThrows(HttpClientResponseException.class, () -> client.exchange(
                DELETE(submissionUrl).cookie(authCookie), String.class
        ).blockingFirst());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, genotypeError.getStatus());
        assertEquals(
                DELETE_GENOTYPE_DATA_NOT_ALLOWED_ERROR_MESSAGE,
                genotypeError.getResponse().getBody(String.class).orElse(null)
        );

        HttpResponse<String> preservedSubmission = client.exchange(
                GET(submissionUrl + "?details=true").cookie(authCookie), String.class
        ).blockingFirst();
        SampleSubmission preserved = gson.fromJson(
                JsonParser.parseString(preservedSubmission.body()).getAsJsonObject().getAsJsonObject("result"),
                SampleSubmission.class
        );
        assertEquals(96, preserved.getSamples().size());
        assertEquals(1, preserved.getPlates().size());

        dsl.deleteFrom(GENOTYPE_IMPORT)
                .where(GENOTYPE_IMPORT.SAMPLE_SUBMISSION_ID.eq(submission.getId()))
                .execute();

        HttpResponse<String> deleteResponse = client.exchange(
                DELETE(submissionUrl).cookie(authCookie), String.class
        ).blockingFirst();
        assertEquals(HttpStatus.OK, deleteResponse.getStatus());

        HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class, () -> client.exchange(
                GET(submissionUrl + "?details=true").cookie(authCookie), String.class
        ).blockingFirst());
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
    }


    private Pair<SampleSubmission, List<Map<String, Object>>> createSubmission(Program program) throws IOException, InterruptedException {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=SampleImport").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        String sampleMappingId = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        var submissionData = makeSubmission();
        var submission = new SampleSubmission();
        submission.setName("test-"+UUID.randomUUID());

        JsonObject importResult = importTestUtils.uploadAndFetch(
                writeSubmissionToFile(submissionData),
                Map.of(SUBMISSION_NAME, submission.getName()),
                true,
                client,
                program,
                sampleMappingId);
        JsonArray previewRows = importResult.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(96, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();
        BrAPISample sample = new Gson().fromJson(row.getAsJsonObject("sample").getAsJsonObject("brAPIObject"), BrAPISample.class);
        BrAPIExternalReference xref = Utilities.getExternalReference(sample.getExternalReferences(), Utilities.generateReferenceSource(BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.PLATE_SUBMISSIONS)).get();
        submission.setId(UUID.fromString(xref.getReferenceId()));

        return Pair.of(submission, submissionData);
    }

    private List<Map<String, Object>> makeSubmission() {
        List<Map<String, Object>> validFile = new ArrayList<>();
        int germGidCounter = 1;
        for(int i = 0; i < 8; i++) {
            for(int j = 0; j < 12; j++) {
                Map<String, Object> validRow = new HashMap<>();
                validRow.put(Columns.PLATE_ID, "valid_1");
                validRow.put(Columns.ROW, Character.toString('A' + i));
                validRow.put(Columns.COLUMN, j+1);
                validRow.put(Columns.ORGANISM, "TEST");
                validRow.put(Columns.SPECIES, "TEST");
                validRow.put(Columns.GERMPLASM_NAME, "");
                validRow.put(Columns.GERMPLASM_GID, germGidCounter++);
                validRow.put(Columns.OBS_UNIT_ID, "");
                validRow.put(Columns.TISSUE, "TEST");
                validRow.put(Columns.COMMENTS, "Test sample");
                validFile.add(validRow);
            }
        }

        return validFile;
    }

    private String createExperiment(Program program) throws IOException, InterruptedException {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=ExperimentsTemplateMap").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        String expMappingId = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        JsonObject importResult = importTestUtils.uploadAndFetchWorkflow(
                importTestUtils.writeExperimentDataToFile(List.of(makeExpImportRow("Env1")), null, false, false, null),
                null,
                true,
                client,
                program,
                expMappingId,
                newExperimentWorkflowId);
        return importResult
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("trial").getAsJsonObject()
                .get("id").getAsString();
    }

    private Map<String, Object> makeExpImportRow(String environment) {
        Map<String, Object> row = new HashMap<>();
        row.put(ExperimentObservation.Columns.GERMPLASM_GID, "1");
        row.put(ExperimentObservation.Columns.TEST_CHECK, "T");
        row.put(ExperimentObservation.Columns.EXP_TITLE, "Test Exp");
        row.put(ExperimentObservation.Columns.EXP_UNIT, "Plot");
        row.put(ExperimentObservation.Columns.EXP_TYPE, "Phenotyping");
        row.put(ExperimentObservation.Columns.ENV, environment);
        row.put(ExperimentObservation.Columns.ENV_LOCATION, "Location A");
        row.put(ExperimentObservation.Columns.ENV_YEAR, "2023");
        row.put(ExperimentObservation.Columns.EXP_UNIT_ID, "a-1");
        row.put(ExperimentObservation.Columns.REP_NUM, "1");
        row.put(ExperimentObservation.Columns.BLOCK_NUM, "1");
        row.put(ExperimentObservation.Columns.ROW, "1");
        row.put(ExperimentObservation.Columns.COLUMN, "1");
        return row;
    }

    public File writeSubmissionToFile(List<Map<String, Object>> data) throws IOException {
        File file = File.createTempFile("test", ".csv");

        List<Column> columns = new ArrayList<>();
        columns.add(Column.builder().value(Columns.PLATE_ID).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.ROW).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.COLUMN).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(Columns.ORGANISM).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.SPECIES).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.GERMPLASM_NAME).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.GERMPLASM_GID).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(Columns.OBS_UNIT_ID).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.TISSUE).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.COMMENTS).dataType(Column.ColumnDataType.STRING).build());

        ByteArrayOutputStream byteArrayOutputStream = CSVWriter.writeToCSV(columns, data);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(byteArrayOutputStream.toByteArray());

        return file;
    }

    private List<BrAPIGermplasm> createGermplasm(int numToCreate) {
        List<BrAPIGermplasm> germplasm = new ArrayList<>();
        for (int i = 0; i < numToCreate; i++) {
            String gid = ""+(i+1);
            BrAPIGermplasm testGermplasm = new BrAPIGermplasm();
            testGermplasm.setGermplasmName(String.format("Germplasm %s [TEST-%s]", gid, gid));
            testGermplasm.setSeedSource("Wild");
            testGermplasm.setAccessionNumber(gid);
            testGermplasm.setDefaultDisplayName(String.format("Germplasm %s", gid));
            JsonObject additionalInfo = new JsonObject();
            additionalInfo.addProperty("importEntryNumber", gid);
            additionalInfo.addProperty("breedingMethod", "Allopolyploid");
            testGermplasm.setAdditionalInfo(additionalInfo);
            List<BrAPIExternalReference> externalRef = new ArrayList<>();
            BrAPIExternalReference testReference = new BrAPIExternalReference();
            testReference.setReferenceSource(BRAPI_REFERENCE_SOURCE);
            testReference.setReferenceID(UUID.randomUUID().toString());
            externalRef.add(testReference);
            testGermplasm.setExternalReferences(externalRef);
            germplasm.add(testGermplasm);
        }

        return germplasm;
    }
}
