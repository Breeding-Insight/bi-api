package org.breedinginsight.brapi.v2;

import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.services.BrAPITrialService;
import org.breedinginsight.brapps.importer.ImportTestUtils;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.daos.RoleDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramUserRoleEntity;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.ProgramUserDAO;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.experiment.ExperimentFileColumns;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.utilities.DatasetUtil;
import org.breedinginsight.utilities.FileUtil;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;
import javax.inject.Inject;
import java.io.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static io.micronaut.http.HttpRequest.*;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExperimentControllerIntegrationTest extends BrAPITest {

    private Program program;
    private String experimentId;
    private List<String> envIds = new ArrayList<>();
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final List<Column> columns = ExperimentFileColumns.getOrderedColumns();
    private List<Trait> traits;
    private User testUser;
    private User otherTestUser;

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
    private BrAPITrialService experimentService;
    @Inject
    private BrAPIGermplasmDAO germplasmDAO;
    @Inject
    private ProgramUserDAO programUserDAO;
    @Inject
    private RoleDao roleDao;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    private String newExperimentWorkflowId;
    private final Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
            (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
            .create();

    @BeforeAll
    void setup() throws Exception {
        FannyPack fp = FannyPack.fill("src/test/resources/sql/ImportControllerIntegrationTest.sql");
        ImportTestUtils importTestUtils = new ImportTestUtils();
        newExperimentWorkflowId = importTestUtils.getExperimentWorkflowId(client, 0);
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        FannyPack brapiFp = FannyPack.fill("src/test/resources/sql/brapi/species.sql");

        // Test User
        testUser = userDAO.getUserByOrcId(TestTokenValidator.TEST_USER_ORCID).orElseThrow(Exception::new);
        otherTestUser = userDAO.getUserByOrcId(TestTokenValidator.OTHER_TEST_USER_ORCID).orElseThrow(Exception::new);

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

        // Get experiment import map
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=ExperimentsTemplateMap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        String mappingId = JsonParser.parseString(Objects.requireNonNull(response.body())).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        // Add traits to program
        traits = createTraits(2);
        AuthenticatedUser user = new AuthenticatedUser(testUser.getName(), new ArrayList<>(), testUser.getId(), new ArrayList<>());
        try {
            ontologyService.createTraits(program.getId(), traits, user, false);
        } catch (ValidatorException e) {
            System.err.println(e.getErrors());
            throw e;
        }

        // Add germplasm to program
        List<BrAPIGermplasm> germplasm = createGermplasm(1);
        BrAPIExternalReference newReference = new BrAPIExternalReference();
        newReference.setReferenceSource(String.format("%s/programs", BRAPI_REFERENCE_SOURCE));
        newReference.setReferenceID(program.getId().toString());

        germplasm.forEach(germ -> germ.getExternalReferences().add(newReference));

        germplasmDAO.createBrAPIGermplasm(germplasm, program.getId(), null);

        // Make test experiment import
        Map<String, Object> row1 = makeExpImportRow("Env1");
        Map<String, Object> row2 = makeExpImportRow("Env2");

        // Add test observation data
        for (Trait trait : traits) {
            Random random = new Random();

            // TODO: test for sending obs data as double.
            //  A float is returned from the backend instead of double. there is a separate card to fix this.
            // Double val1 = Math.random();

            Float val1 = random.nextFloat();
            row1.put(trait.getObservationVariableName(), val1);
        }

        rows.add(row1);
        rows.add(row2);

        // Import test experiment, environments, and any observations
        JsonObject importResult = importTestUtils.uploadAndFetchWorkflow(
                writeDataToFile(rows, traits),
                null,
                true,
                client,
                program,
                mappingId,
                newExperimentWorkflowId);
        experimentId = importResult
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(0).getAsJsonObject()
                .get("trial").getAsJsonObject()
                .get("id").getAsString();
        // Add environmentIds.
        envIds.add(getEnvId(importResult, 0));
        envIds.add(getEnvId(importResult, 1));
    }

    /**
     * Tests all 18 permutations of
     * 3 formats: [CSV, XLS, XLSX],
     * 3 env query param options: [None, 1, 2],
     * 2 timestamp options [with, without].
     * @param includeTimestamps when true, timestamp columns are requested.
     * @param extension the file extension requested.
     * @param numberOfEnvsRequested 0 -> no env params sent, >= 1 -> requested number of envIds sent as query params.
   */
    @ParameterizedTest
    @CsvSource(value = {
            "true,CSV,0", "false,CSV,0",
            "true,XLS,0", "false,XLS,0",
            "true,XLSX,0", "false,XLSX,0",
            "true,CSV,1", "false,CSV,1",
            "true,XLS,1", "false,XLS,1",
            "true,XLSX,1", "false,XLSX,1",
            "true,CSV,2", "false,CSV,2",
            "true,XLS,2", "false,XLS,2",
            "true,XLSX,2", "false,XLSX,2",})
    @SneakyThrows
    void downloadDatasets(boolean includeTimestamps, String extension, int numberOfEnvsRequested) {
        // How many columns are expected in the output?
        int expectedColNumber = columns.size();
        if (includeTimestamps) {
            expectedColNumber += traits.size();
        }
        // Temporary directory to extract zip into, test will clean up after use.
        String tempDir = "./zip_temp_dir/";
        // If more than 1 envId is sent as a query param, a zip file is expected response.
        boolean zipExpected = numberOfEnvsRequested > 1;
        // Download test experiment
        String envParam = "all=true";
        if (numberOfEnvsRequested > 0) {
            // Build environment query param with 1 or all envIds.
            String envs = numberOfEnvsRequested == 1 ? envIds.get(0) : String.join(",", envIds);
            envParam = "environments=" + envs;
        }
        // Get datasetId to include in export request.
        String datasetId = experimentService.getDatasetsMetadata(program, UUID.fromString(experimentId)).stream().findFirst().get().getId().toString();
        Flowable<HttpResponse<byte[]>> call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/export?includeTimestamps=%s&%s&fileExtension=%s&datasetId=%s",
                        program.getId().toString(), experimentId, includeTimestamps, envParam, extension, datasetId))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), byte[].class
        );
        HttpResponse<byte[]> response = call.blockingFirst();

        // Assert 200 response
        assertEquals(HttpStatus.OK, response.getStatus());

        ByteArrayInputStream bodyStream = new ByteArrayInputStream(Objects.requireNonNull(response.body()));

        // Assert file format fidelity
        Map<String, String> mediaTypeByExtension = new HashMap<>();
        mediaTypeByExtension.put("CSV", FileType.CSV.getMimeType());
        mediaTypeByExtension.put("XLS", FileType.XLS.getMimeType());
        mediaTypeByExtension.put("XLSX", FileType.XLSX.getMimeType());
        mediaTypeByExtension.put("ZIP", FileType.ZIP.getMimeType());
        String downloadMediaType = response.getHeaders().getContentType().orElseThrow(Exception::new);
        // If zip is expected, check that it is indeed a zip file, then unzip and check each file.
        if (zipExpected) {
            assertEquals(FileType.ZIP.getMimeType(), downloadMediaType);
            // Unzip into tempDir.
            TestUtils.unzipFile(bodyStream, tempDir);

            for (File file : Objects.requireNonNull(new File(tempDir).listFiles())) {
                FileInputStream fileStream = new FileInputStream(file);
                // Filter rows based on env in file name.
                List<Map<String, Object>> filteredRows = rows.stream()
                        .filter(row -> file.getName().contains(row.get(ExperimentObservation.Columns.ENV).toString()))
                        .collect(Collectors.toList());
                parseAndCheck(fileStream, extension, true, filteredRows, includeTimestamps, expectedColNumber);
            }
        }
        else {
            assertEquals(mediaTypeByExtension.get(extension), downloadMediaType);
            // All (both) rows when 0 or 2 envs sent, first row when 1 env sent as query param.
            List<Map<String, Object>> filteredRows = numberOfEnvsRequested == 1 ? List.of(rows.get(0)) : rows;
            parseAndCheck(bodyStream, extension, numberOfEnvsRequested > 0, filteredRows, includeTimestamps, expectedColNumber);
        }
        // Remove temp directory after each test run.
        FileUtils.deleteDirectory(new File(tempDir));
    }

    /**
     * Tests creating and subsequently downloading a sub-entity dataset.
     * It also ensures no regressions with the top-level dataset download are introduced by the sub-entity features.
     */
    @ParameterizedTest
    @CsvSource(value = {"CSV", "XLSX", "XLS"})
    @SneakyThrows
    void downloadSubEntityDataset(String extension) {

        // Create sub-entity dataset.
        Flowable<HttpResponse<byte[]>> postCall = client.exchange(
                POST(String.format("/programs/%s/experiments/%s/dataset",
                                program.getId().toString(), experimentId),
                        "{\"name\":\"Plant\",\"repeatedMeasures\":3}")
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")),
                byte[].class);
        HttpResponse<byte[]> postResponse = postCall.blockingFirst();

        // Assert 200 response
        assertEquals(HttpStatus.OK, postResponse.getStatus());

        // Get top-level datasetId to include in export request.
        BrAPITrial experiment = experimentService.getTrialDataByUUID(program.getId(), UUID.fromString(experimentId), false);
        String topLevelDatasetId = DatasetUtil.getTopLevelDataset(experiment).getId().toString();
        Flowable<HttpResponse<byte[]>> topLevelExportCall = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/export?all=true&includeTimestamps=false&fileExtension=%s&datasetId=%s",
                        program.getId().toString(), experimentId, extension, topLevelDatasetId))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), byte[].class
        );
        HttpResponse<byte[]> topLevelResponse = topLevelExportCall.blockingFirst();

        // Assert 200 response
        assertEquals(HttpStatus.OK, topLevelResponse.getStatus());

        // Assert file format fidelity
        Map<String, String> mediaTypeByExtension = new HashMap<>();
        mediaTypeByExtension.put("CSV", FileType.CSV.getMimeType());
        mediaTypeByExtension.put("XLS", FileType.XLS.getMimeType());
        mediaTypeByExtension.put("XLSX", FileType.XLSX.getMimeType());
        String downloadMediaType = topLevelResponse.getHeaders().getContentType().orElseThrow(Exception::new);
        assertEquals(mediaTypeByExtension.get(extension), downloadMediaType);

        // Check file contents.
        ByteArrayInputStream bodyStream = new ByteArrayInputStream(Objects.requireNonNull(topLevelResponse.body()));
        parseAndCheck(bodyStream, extension, false, rows, false, 25);

        // Make sub-entity dataset export request.
        String plantDatasetId = DatasetUtil.getDatasetIdByNameFromJson(experiment.getAdditionalInfo().getAsJsonArray("datasets"), "Plant");
        Flowable<HttpResponse<byte[]>> plantExportCall = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/export?all=true&includeTimestamps=false&fileExtension=%s&datasetId=%s",
                        program.getId().toString(), experimentId, extension, plantDatasetId))
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), byte[].class
        );
        HttpResponse<byte[]> plantResponse = plantExportCall.blockingFirst();

        // Assert 200 response
        assertEquals(HttpStatus.OK, plantResponse.getStatus());

        // Assert file format fidelity
        assertEquals(mediaTypeByExtension.get(extension), plantResponse.getHeaders().getContentType().orElseThrow(Exception::new));

        // The expected contents of the exported Plant dataset (3 sub-obs units for each top-level unit were requested).
        List<Map<String, Object>> plantRows = buildSubEntityRows(rows, "Plant", 3);

        // Check file contents.
        ByteArrayInputStream plantBodyStream = new ByteArrayInputStream(Objects.requireNonNull(plantResponse.body()));
        parseAndCheck(plantBodyStream, extension, false, plantRows, false, 23);
    }

    /**
     * Tests for Experimental Collaborator endpoints
     */

    /**
     * Create Experimental Collaborator Invalid Id
     *   GIVEN POST /v1/programs/{programId}/experiments/{experimentId}/collaborators
     *   WHEN an invalid id is passed in the body of the request
     *   THEN response should be 404
     */
    @Test
    public void postExperimentalCollaboratorsInvalidId() {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("programUserId", "f47ac10b-58cc-4372-a567-0e02b2c3d479");

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(String.format("/programs/%s/experiments/%s/collaborators", program.getId().toString(), experimentId), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<String> response = call.blockingFirst();
        });

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
    }

    /**
     * Create and Delete Experimental Collaborator
     */
    @Test
    public void postAndDeleteExperimentalCollaborator() {

        RoleEntity roleEntity = roleDao.fetchByDomain("Experimental Collaborator").get(0);

        ProgramUserRoleEntity programUserEntity = ProgramUserRoleEntity.builder()
                .userId(otherTestUser.getId())
                .programId(program.getId())
                .roleId(roleEntity.getId())
                .createdBy(testUser.getId())
                .updatedBy(testUser.getId())
                .build();

        programUserDAO.insert(programUserEntity);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", otherTestUser.getId().toString());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(String.format("/programs/%s/experiments/%s/collaborators", program.getId().toString(), experimentId), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String id = result.get("id").getAsString();
        Assertions.assertNotEquals(null, id);

        // check count = 1
        call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/collaborators?active=true", program.getId().toString(), experimentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(1, data.size());

        // now delete
        call = client.exchange(
                DELETE(String.format("/programs/%s/experiments/%s/collaborators/%s", program.getId().toString(), experimentId, id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // check count = 0
        call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/collaborators?active=true", program.getId().toString(), experimentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        data = result.getAsJsonArray("data");
        assertEquals(0, data.size());

        // clean up
        programUserDAO.deleteProgramUserRoles(program.getId(), otherTestUser.getId());

    }

    /**
     * Get Experimental Collaborators None
     *   GIVEN GET /v1/programs/{programId}/experiments/{experimentId}/collaborators?active=true|false
     *   WHEN no users have been added as experiment collaborators
     *   AND no program users with Experimental Collaborator role exist in program
     *   THEN response should be an empty array regardless of active query parameter value
     *
     *   test-registered-user has Program Administration role in program
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void getExperimentalCollaboratorsNone(boolean active) {

        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/collaborators?active=%s", program.getId().toString(), experimentId, active))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");
        assertEquals(0, data.size());
    }

    /**
     * Get Experimental Collaborators No Active
     *   GIVEN GET /v1/programs/{programId}/experiments/{experimentId}/collaborators?active=true|false
     *   WHEN no users have been added as experiment collaborators
     *   AND one or more program users with Experimental Collaborator role exist in program
     *   THEN response should be:
     *      empty array when active=true
     *      array with program user when active=false
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void getExperimentalCollaboratorsNoActive(boolean active) {

        // add a program user with the experimental collaborator role
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        dsl.execute(securityFp.get("InsertProgramRolesExperimentalCollaborator"), otherTestUser.getId().toString(), program.getId());

        ProgramUser programUser = programUserDAO.getProgramUser(program.getId(), otherTestUser.getId());

        Flowable<HttpResponse<String>> call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/collaborators?active=%s", program.getId().toString(), experimentId, active))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        if (active) {
            assertEquals(0, data.size());
        } else {
            assertEquals(1, data.size());
            // TODO: check user details
            JsonObject collaborator = data.get(0).getAsJsonObject();
            // Currently not returning key rather than key with null
            //assertEquals(collaborator.get("collaboratorId").getAsString(),null, "Expected null for id");
            assertEquals(collaborator.get("userId").getAsString(), otherTestUser.getId().toString(), "Unexpected userId");
            assertEquals(collaborator.get("programUserId").getAsString(), programUser.getId().toString(), "Unexpected programUserId");
            assertEquals(collaborator.get("name").getAsString(), otherTestUser.getName(), "Unexpected name");
            assertEquals(collaborator.get("email").getAsString(), otherTestUser.getEmail(), "Unexpected email");
            assertEquals(collaborator.get("active").getAsBoolean(), false, "Unexpected active status");
        }

        // delete program user from setup
        // NOTE: if test fails this may not be run and could impact other tests results
        dsl.execute(securityFp.get("DeleteProgramUser"), otherTestUser.getId().toString());

    }

    /**
     * Get Experimental Collaborators Active
     *   GIVEN GET /v1/programs/{programId}/experiments/{experimentId}/collaborators?active=true|false
     *   WHEN program user has been added to experiment as collaborator
     *   AND program user with Experimental Collaborator role exists in program
     *   THEN response should be:
     *      array with program user when active=true
     *      empty array when active=false
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void getExperimentalCollaboratorsActive(boolean active) {
        // add a program user with the experimental collaborator role
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        dsl.execute(securityFp.get("InsertProgramRolesExperimentalCollaborator"), otherTestUser.getId().toString(), program.getId());

        // add user as experimental collaborator
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", otherTestUser.getId().toString());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(String.format("/programs/%s/experiments/%s/collaborators", program.getId().toString(), experimentId), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String id = result.get("id").getAsString();
        Assertions.assertNotEquals(null, id);

        // get experimental collaborators
        call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/collaborators?active=%s", program.getId().toString(), experimentId, active))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        if (active) {
            assertEquals(1, data.size());
        } else {
            assertEquals(0, data.size());
        }

        // cleanup - delete collaborator
        call = client.exchange(
                DELETE(String.format("/programs/%s/experiments/%s/collaborators/%s", program.getId().toString(), experimentId, id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // delete program user from setup
        // NOTE: if test fails this may not be run and could impact other tests results
        dsl.execute(securityFp.get("DeleteProgramUser"), otherTestUser.getId().toString());
    }

    /**
     * Get Experimental Collaborators Deactivated from Program
     *   GIVEN GET /v1/programs/{programId}/experiments/{experimentId}/collaborators?active=true|false
     *   WHEN program user has been added to experiment as collaborator
     *   AND after being added as a collaborator, program user is deactivated from program
     *   THEN response should be empty array regardless of active query parameter value (assumes single user)
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void getExperimentalCollaboratorsDeactivated(boolean active) {
        // add a program user with the experimental collaborator role
        FannyPack securityFp = FannyPack.fill("src/test/resources/sql/ProgramSecuredAnnotationRuleIntegrationTest.sql");
        dsl.execute(securityFp.get("InsertProgramRolesExperimentalCollaborator"), otherTestUser.getId().toString(), program.getId());

        // add user as experimental collaborator
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", otherTestUser.getId().toString());

        Flowable<HttpResponse<String>> call = client.exchange(
                POST(String.format("/programs/%s/experiments/%s/collaborators", program.getId().toString(), experimentId), requestBody.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        String id = result.get("id").getAsString();
        Assertions.assertNotEquals(null, id);

        // deactivate program user
        call = client.exchange(
                DELETE(String.format("/programs/%s/users/%s", program.getId().toString(), otherTestUser.getId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // get experimental collaborators
        call = client.exchange(
                GET(String.format("/programs/%s/experiments/%s/collaborators?active=%s", program.getId().toString(), experimentId, active))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        response = call.blockingFirst();

        assertEquals(HttpStatus.OK, response.getStatus());

        result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        JsonArray data = result.getAsJsonArray("data");

        // should be empty regardless of active query parameter value
        assertEquals(0, data.size());

        // cleanup - remove collaborator
        call = client.exchange(
                DELETE(String.format("/programs/%s/experiments/%s/collaborators/%s", program.getId().toString(), experimentId, id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );

        response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        // delete program user from setup
        // NOTE: if test fails this may not be run and could impact other tests results
        dsl.execute(securityFp.get("DeleteProgramUser"), otherTestUser.getId().toString());
    }


    private List<Map<String, Object>> buildSubEntityRows(List<Map<String, Object>> topLevelRows, String entityName, int repeatedMeasures) {
        List<Map<String, Object>> plantRows = new ArrayList<>();
        for (Map<String, Object> row : topLevelRows) {
            for (Integer i=1; i<=repeatedMeasures; i++) {
                // Deep copy map entries.
                Map<String, Object> plantRow = new HashMap<>(row);

                plantRow.put("Exp Unit", entityName);
                plantRow.put("Exp Unit ID", i.toString());
                plantRow.remove("tt_test_1");
                plantRow.remove("tt_test_2");
                plantRows.add(plantRow);
            }
        }
        return plantRows;
    }

    private File writeDataToFile(List<Map<String, Object>> data, List<Trait> traits) throws IOException {
        File file = File.createTempFile("test", ".csv");

        if(traits != null) {
            traits.forEach(trait -> columns.add(
                        Column.builder()
                                .value(trait.getObservationVariableName())
                                .dataType(Column.ColumnDataType.STRING)
                                .build())
            );
        }
        ByteArrayOutputStream byteArrayOutputStream = CSVWriter.writeToCSV(columns, data);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(byteArrayOutputStream.toByteArray());

        return file;
    }

    private Map<String, Object> makeExpImportRow(String environment) {
        Map<String, Object> row = new HashMap<>();
        row.put(ExperimentObservation.Columns.GERMPLASM_GID, "1");
        row.put(ExperimentObservation.Columns.TEST_CHECK, "T");
        row.put(ExperimentObservation.Columns.EXP_TITLE, "Test Exp");
        row.put(ExperimentObservation.Columns.EXP_UNIT, "Plot");
        row.put(ExperimentObservation.Columns.SUB_OBS_UNIT, "");
        row.put(ExperimentObservation.Columns.EXP_TYPE, "Phenotyping");
        row.put(ExperimentObservation.Columns.ENV, environment);
        row.put(ExperimentObservation.Columns.ENV_LOCATION, "Location A");
        row.put(ExperimentObservation.Columns.ENV_YEAR, "2023");
        row.put(ExperimentObservation.Columns.EXP_UNIT_ID, "a-1");
        row.put(ExperimentObservation.Columns.SUB_UNIT_ID, "");
        row.put(ExperimentObservation.Columns.REP_NUM, "1");
        row.put(ExperimentObservation.Columns.BLOCK_NUM, "1");
        row.put(ExperimentObservation.Columns.ROW, "1");
        row.put(ExperimentObservation.Columns.COLUMN, "1");
        row.put(ExperimentObservation.Columns.LAT, "");
        row.put(ExperimentObservation.Columns.LONG, "");
        row.put(ExperimentObservation.Columns.ELEVATION, "");
        row.put(ExperimentObservation.Columns.RTK, "");
        return row;
    }

    private List<Trait> createTraits(int numToCreate) {
        List<Trait> traits = new ArrayList<>();
        for (int i = 0; i < numToCreate; i++) {
            String varName = "tt_test_" + (i + 1);
            traits.add(Trait.builder()
                    .observationVariableName(varName)
                    .fullName(varName)
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

    private void parseAndCheck(InputStream stream,
                               String extension,
                               boolean requestEnv,
                               List<Map<String, Object>> rows,
                               boolean includeTimestamps,
                               Integer expectedColNumber) throws ParsingException {
        Table download = Table.create();
        if (extension.equals("CSV")) {
            download = FileUtil.parseTableFromCsv(stream);
        }
        if (extension.equals("XLS") || extension.equals("XLSX")) {
            download = FileUtil.parseTableFromExcel(stream, 0);
        }

        // Assert import/export fidelity and presence of observation units in export
        checkDownloadTable(requestEnv, rows, download, includeTimestamps, extension, expectedColNumber);
    }

    private void checkDownloadTable(
            boolean requestEnv,
            List<Map<String, Object>> requestedImportRows,
            Table table,
            boolean includeTimestamps,
            String extension,
            Integer expectedColNumber) {
        // Filename is correct: <exp-title>_Observation Dataset [<prog-key>-<exp-seq>]_<environment>_<export-timestamp>
        List<String> expectedEnvNames = requestedImportRows.stream()
                .map(row -> row.get(ExperimentObservation.Columns.ENV).toString()).collect(Collectors.toList());

        assertEquals(expectedColNumber, table.columnCount());

        // Check that requested envs are present.
        expectedEnvNames.forEach(envName -> assertTrue(table.stringColumn("Env").contains(envName)));

        List<Map<String, Object>> matchingImportRows = new ArrayList<>();
        Optional<Map<String, Object>> matchingImportRow;

        for (int rowNum = 0; rowNum < requestedImportRows.size(); rowNum++) {
            Row downloadRow = table.row(rowNum);

            // sort order is not guaranteed to be th same as import, so find import row for corresponding export row
            // by first matching environment, GID and Exp Unit ID
            matchingImportRow = requestedImportRows.stream().filter(row -> {
                String gid = ExperimentObservation.Columns.GERMPLASM_GID;
                String env = ExperimentObservation.Columns.ENV;
                String expUnitId = ExperimentObservation.Columns.EXP_UNIT_ID;
                if (extension.equalsIgnoreCase(FileType.CSV.getName())) {
                    return Integer.parseInt(row.get(gid).toString()) == downloadRow.getInt(gid) &&
                            row.get(env).equals(downloadRow.getString(env)) &&
                            row.get(expUnitId).equals(downloadRow.getObject(expUnitId).toString());
                } else {
                    return row.get(gid).equals(downloadRow.getString(gid)) &&
                            row.get(env).equals(downloadRow.getString(env)) &&
                            row.get(expUnitId).equals(downloadRow.getObject(expUnitId).toString());
                }
            }).findAny();
            assertTrue(matchingImportRow.isPresent() && !matchingImportRow.get().isEmpty());

            // then check the rest of the fields match
            if (isMatchedRow(matchingImportRow.get(), downloadRow)) {
                matchingImportRows.add(matchingImportRow.get());
            }
        }
        assertEquals(requestedImportRows.size(),matchingImportRows.size());

        // Observation units populated.
        assertEquals(0, table.column("ObsUnitID").countMissing());
        // Observation Unit IDs are assigned.
        assertEquals(requestedImportRows.size(), table.column("ObsUnitID").countUnique());
    }

    private boolean isMatchedRow(Map<String, Object> importRow, Row downloadRow) {
        System.out.println("Validating row: " + downloadRow.getRowNumber());
        System.out.println("import columns: " + importRow.size());
        return importRow.entrySet().stream().filter(e -> {
            String header = e.getKey();
            List<Column> importColumns = columns
                    .stream()
                    .filter(col -> header.equals(col.getValue())).collect(Collectors.toList());
            if (importColumns.size() != 1) {
                return false;
            }
            Object expectedVal = null;
            Object downloadedVal = null;
            boolean doCompare = false;

            if (downloadRow.getColumnType(e.getKey()).equals(ColumnType.STRING)) {
                expectedVal = e.getValue().toString();
                downloadedVal = downloadRow.getString(e.getKey());
                doCompare = true;
            }
            if (downloadRow.getColumnType(e.getKey()).equals(ColumnType.INTEGER)) {
                expectedVal = Integer.parseInt(e.getValue().toString());
                downloadedVal = downloadRow.getInt(e.getKey());
                doCompare = true;
            }
            if (downloadRow.getColumnType(e.getKey()).equals(ColumnType.DOUBLE)) {
                expectedVal = Double.parseDouble(e.getValue().toString());
                downloadedVal = downloadRow.getDouble(e.getKey());
                doCompare = true;
            }
            if (downloadRow.getColumnType(e.getKey()).equals(ColumnType.FLOAT)) {
                expectedVal = e.getValue();
                downloadedVal = downloadRow.getFloat(e.getKey());
                doCompare = true;
            }
            System.out.println("Column: "+e.getKey()+", Expected: '"+ expectedVal +"', Received: '" + downloadedVal+"'");
            if(doCompare) {
                assertEquals(expectedVal, downloadedVal);
                return expectedVal.equals(downloadedVal);
            } else {
                return false;
            }
        }).count() == importRow.size();
    }

    private String getEnvId(JsonObject result, int index) {
        return result
                .get("preview").getAsJsonObject()
                .get("rows").getAsJsonArray()
                .get(index).getAsJsonObject()
                .get("study").getAsJsonObject()
                .get("brAPIObject").getAsJsonObject()
                .get("externalReferences").getAsJsonArray()
                .get(2).getAsJsonObject()
                .get("referenceId").getAsString();
    }

}
