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

import com.google.gson.*;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.typeAdapters.PaginationTypeAdapter;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrAPIPagination;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.geno.BrAPIPlate;
import org.brapi.v2.model.geno.BrAPISample;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.*;
import org.breedinginsight.brapps.importer.daos.*;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.imports.sample.SampleSubmissionImport.Columns;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.Column;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.SampleSubmission;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.*;
import org.breedinginsight.services.exceptions.BadRequestException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.util.StringUtils;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

import static io.micronaut.http.HttpRequest.GET;
import static org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields.SUBMISSION_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SampleSubmissionFileImportTest extends BrAPITest {

    private FannyPack securityFp;
    private String mappingId;
    private BiUserEntity testUser;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;
    @Property(name = "brapi.server.core-url")
    private String BRAPI_URL;

    @Inject
    private SpeciesService speciesService;
    @Inject
    private UserDAO userDAO;
    @Inject
    private DSLContext dsl;

    @Inject
    private SpeciesDAO speciesDAO;

    @Inject
    private ProgramService programService;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    private ImportTestUtils importTestUtils;

    @Inject
    private OntologyService ontologyService;

    @Inject
    private BrAPITrialDAO brAPITrialDAO;

    @Inject
    private BrAPIStudyDAO brAPIStudyDAO;

    @Inject
    private BrAPIObservationUnitDAO ouDAO;

    @Inject
    private ProgramLocationService locationService;

    @Inject
    private BrAPIGermplasmDAO germplasmDAO;

    @Inject
    private BrAPIObservationDAO observationDAO;

    @Inject
    private BrAPISeasonDAO seasonDAO;

    @Inject
    private SampleSubmissionService sampleSubmissionService;

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
                                                 (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
                                         .registerTypeAdapter(BrAPIPagination.class, new PaginationTypeAdapter())
                                         .create();

    @BeforeAll
    public void setup() {
        importTestUtils = new ImportTestUtils();
        Map<String, Object> setupObjects = importTestUtils.setup(client, gson, dsl, speciesService, userDAO, super.getBrapiDsl(), "SampleImport");
        mappingId = (String) setupObjects.get("mappingId");
        testUser = (BiUserEntity) setupObjects.get("testUser");
        securityFp = (FannyPack) setupObjects.get("securityFp");

    }

    /*
    Tests
    - valid submission GID
    - valid submission ObsUnitID
    - missing columns error
    - conflicting wells error
    - bad GID error
    - bad ObsUnitID error
     */

    @Test
    @SneakyThrows
    public void importGIDSuccess() {
        Program program = createProgram("Import GID Success", "GIDS", "GIDS", BRAPI_REFERENCE_SOURCE, createGermplasm(96), null);
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

        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(writeDataToFile(validFile), Map.of(SUBMISSION_NAME, "test-"+UUID.randomUUID()), true, client, program, mappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, program, mappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + result);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(96, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("NEW", row.getAsJsonObject("plate").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("sample").get("state").getAsString());

        BrAPISample sample = new Gson().fromJson(row.getAsJsonObject("sample").getAsJsonObject("brAPIObject"), BrAPISample.class);
        BrAPIExternalReference xref = Utilities.getExternalReference(sample.getExternalReferences(), Utilities.generateReferenceSource(BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.PLATE_SUBMISSIONS)).get();

        assertFileSaved(validFile, program, UUID.fromString(xref.getReferenceId()));
    }

    @Test
    @SneakyThrows
    public void importObsUnitIdSuccess() {
        Program program = createProgram("Import ObsUnitID success", "OBSID", "OBSID", BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);

        var experimentId = createExperiment(program);

        BrAPITrial trial = brAPITrialDAO.getTrialById(program.getId(), UUID.fromString(experimentId)).get();

        List<BrAPIObservationUnit> ous = ouDAO.getObservationUnitsForTrialDbId(program.getId(), trial.getTrialDbId());
        BrAPIExternalReference obsUnitId = Utilities.getExternalReference(ous.get(0).getExternalReferences(), Utilities.generateReferenceSource(BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS)).get();

        List<Map<String, Object>> validFile = new ArrayList<>();

        Map<String, Object> validRow = new HashMap<>();
        validRow.put(Columns.PLATE_ID, "valid_1");
        validRow.put(Columns.ROW, "A");
        validRow.put(Columns.COLUMN, 1);
        validRow.put(Columns.ORGANISM, "TEST");
        validRow.put(Columns.SPECIES, "TEST");
        validRow.put(Columns.GERMPLASM_NAME, "");
        validRow.put(Columns.GERMPLASM_GID, "");
        validRow.put(Columns.OBS_UNIT_ID, obsUnitId.getReferenceId());
        validRow.put(Columns.TISSUE, "TEST");
        validRow.put(Columns.COMMENTS, "Test sample");
        validFile.add(validRow);

        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(writeDataToFile(validFile), Map.of(SUBMISSION_NAME, "test-"+UUID.randomUUID()), true, client, program, mappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, program, mappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + result);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("NEW", row.getAsJsonObject("plate").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("sample").get("state").getAsString());

        BrAPISample sample = new Gson().fromJson(row.getAsJsonObject("sample").getAsJsonObject("brAPIObject"), BrAPISample.class);
        BrAPIExternalReference xref = Utilities.getExternalReference(sample.getExternalReferences(), Utilities.generateReferenceSource(BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.PLATE_SUBMISSIONS)).get();

        assertFileSaved(validFile, program, UUID.fromString(xref.getReferenceId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void importMissingGIDAndObsUnitIdFailure(boolean commit) {
        Program program = createProgram("Missing GID/ObsUnit ID " + (commit ? "C" : "P"), "MGIOB"+ (commit ? "C" : "P"), "MGIOB"+ (commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, null, null);
        List<Map<String, Object>> validFile = new ArrayList<>();

        Map<String, Object> validRow = new HashMap<>();
        validRow.put(Columns.PLATE_ID, "valid_1");
        validRow.put(Columns.ROW, Character.toString('A'));
        validRow.put(Columns.COLUMN, 1);
        validRow.put(Columns.ORGANISM, "TEST");
        validRow.put(Columns.SPECIES, "TEST");
        validRow.put(Columns.GERMPLASM_NAME, "");
        validRow.put(Columns.GERMPLASM_GID, "");
        validRow.put(Columns.OBS_UNIT_ID, "");
        validRow.put(Columns.TISSUE, "TEST");
        validRow.put(Columns.COMMENTS, "Test sample");
        validFile.add(validRow);

        uploadAndVerifyFailure(program, writeDataToFile(validFile), Columns.GERMPLASM_GID, commit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void verifyMissingDataThrowsError(boolean commit) {
        Program program = createProgram("Missing Req Cols "+(commit ? "C" : "P"), "MISS"+(commit ? "C" : "P"), "MISS"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(96), null);
        Map<String, Object> base = new HashMap<>();
        base.put(Columns.PLATE_ID, "valid_1");
        base.put(Columns.ROW, "A");
        base.put(Columns.COLUMN, 1);
        base.put(Columns.ORGANISM, "TEST");
        base.put(Columns.SPECIES, "TEST");
        base.put(Columns.GERMPLASM_NAME, "");
        base.put(Columns.GERMPLASM_GID, "1");
        base.put(Columns.OBS_UNIT_ID, "");
        base.put(Columns.TISSUE, "TEST");
        base.put(Columns.COMMENTS, "Test sample");

        createUploadAndVerifyFailure(program, base, Columns.PLATE_ID, commit);
        createUploadAndVerifyFailure(program, base, Columns.ROW, commit);
        createUploadAndVerifyFailure(program, base, Columns.COLUMN, commit);
        createUploadAndVerifyFailure(program, base, Columns.ORGANISM, commit);
        createUploadAndVerifyFailure(program, base, Columns.TISSUE, commit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void importInvalidGIDFailure(boolean commit) {
        Program program = createProgram("Invalid GID " + (commit ? "C" : "P"), "INGID"+ (commit ? "C" : "P"), "INGID"+ (commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, null, null);
        List<Map<String, Object>> validFile = new ArrayList<>();

        Map<String, Object> validRow = new HashMap<>();
        validRow.put(Columns.PLATE_ID, "valid_1");
        validRow.put(Columns.ROW, Character.toString('A'));
        validRow.put(Columns.COLUMN, 1);
        validRow.put(Columns.ORGANISM, "TEST");
        validRow.put(Columns.SPECIES, "TEST");
        validRow.put(Columns.GERMPLASM_NAME, "");
        validRow.put(Columns.GERMPLASM_GID, "");
        validRow.put(Columns.OBS_UNIT_ID, "");
        validRow.put(Columns.TISSUE, "TEST");
        validRow.put(Columns.COMMENTS, "Test sample");
        validFile.add(validRow);

        uploadAndVerifyFailure(program, writeDataToFile(validFile), Columns.GERMPLASM_GID, commit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void importInvalidObsUnitIdFailure(boolean commit) {
        Program program = createProgram("Invalid ObsUnit ID " + (commit ? "C" : "P"), "INOBS"+ (commit ? "C" : "P"), "INOBS"+ (commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, null, null);
        List<Map<String, Object>> validFile = new ArrayList<>();

        Map<String, Object> validRow = new HashMap<>();
        validRow.put(Columns.PLATE_ID, "valid_1");
        validRow.put(Columns.ROW, Character.toString('A'));
        validRow.put(Columns.COLUMN, 1);
        validRow.put(Columns.ORGANISM, "TEST");
        validRow.put(Columns.SPECIES, "TEST");
        validRow.put(Columns.GERMPLASM_NAME, "");
        validRow.put(Columns.GERMPLASM_GID, "");
        validRow.put(Columns.OBS_UNIT_ID, "hgfhgfhg");
        validRow.put(Columns.TISSUE, "TEST");
        validRow.put(Columns.COMMENTS, "Test sample");
        validFile.add(validRow);

        uploadAndVerifyFailure(program, writeDataToFile(validFile), Columns.OBS_UNIT_ID, commit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void importConflictingWellsFailure(boolean commit) {
        Program program = createProgram("Conflicting Wells " + (commit ? "C" : "P"), "WELL"+ (commit ? "C" : "P"), "WELL"+ (commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(2), null);
        List<Map<String, Object>> validFile = new ArrayList<>();

        Map<String, Object> validRow = new HashMap<>();
        validRow.put(Columns.PLATE_ID, "valid_1");
        validRow.put(Columns.ROW, Character.toString('A'));
        validRow.put(Columns.COLUMN, 1);
        validRow.put(Columns.ORGANISM, "TEST");
        validRow.put(Columns.SPECIES, "TEST");
        validRow.put(Columns.GERMPLASM_NAME, "");
        validRow.put(Columns.GERMPLASM_GID, 1);
        validRow.put(Columns.OBS_UNIT_ID, "");
        validRow.put(Columns.TISSUE, "TEST");
        validRow.put(Columns.COMMENTS, "Test sample");
        validFile.add(validRow);
        validRow = new HashMap<>();
        validRow.put(Columns.PLATE_ID, "valid_1");
        validRow.put(Columns.ROW, Character.toString('A'));
        validRow.put(Columns.COLUMN, 1);
        validRow.put(Columns.ORGANISM, "TEST");
        validRow.put(Columns.SPECIES, "TEST");
        validRow.put(Columns.GERMPLASM_NAME, "");
        validRow.put(Columns.GERMPLASM_GID, 2);
        validRow.put(Columns.OBS_UNIT_ID, "");
        validRow.put(Columns.TISSUE, "TEST");
        validRow.put(Columns.COMMENTS, "Test sample");
        validFile.add(validRow);

        uploadAndVerifyFailure(program, writeDataToFile(validFile), Columns.ROW + "/" + Columns.COLUMN, commit);
    }

    private void assertFileSaved(List<Map<String, Object>> validFile, Program program, UUID submissionId) throws ApiException {
        Optional<SampleSubmission> submission = sampleSubmissionService.getSampleSubmission(program, submissionId, true);
        assertTrue(submission.isPresent(), "Could not find sample submission by ID: " + submissionId);
        for(var row : validFile) {
            assertRowSaved(row, program, submission.get());
        }
    }

    private Map<String, Object> assertRowSaved(Map<String, Object> expected, Program program, SampleSubmission submission) {
        Map<String, Object> ret = new HashMap<>();

        Optional<BrAPIPlate> plate = submission.getPlates().stream().filter(p -> p.getPlateName().equals(expected.get(Columns.PLATE_ID))).findFirst();
        assertTrue(plate.isPresent(), "plate not found");

        Optional<BrAPISample> sample = submission.getSamples().stream().filter(s -> s.getPlateName().equals(expected.get(Columns.PLATE_ID))
                                                                                    && s.getRow().equals(expected.get(Columns.ROW))
                                                                                    && s.getColumn().equals(expected.get(Columns.COLUMN)))
                .findFirst();
        assertTrue(sample.isPresent(), String.format("sample %s%s not found", expected.get(Columns.ROW), expected.get(Columns.COLUMN)));

        assertEquals(expected.get(Columns.ORGANISM), sample.get().getAdditionalInfo().get(BrAPIAdditionalInfoFields.SAMPLE_ORGANISM).getAsString());
        assertEquals(expected.get(Columns.SPECIES), sample.get().getAdditionalInfo().get(BrAPIAdditionalInfoFields.SAMPLE_SPECIES).getAsString());
        assertTrue(sample.get().getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_NAME));
        assertEquals(expected.get(Columns.TISSUE), sample.get().getTissueType());
        assertEquals(expected.get(Columns.COMMENTS), sample.get().getSampleDescription());

        if(StringUtils.isNotBlank((String)expected.get(Columns.OBS_UNIT_ID))) {
            assertTrue(sample.get().getAdditionalInfo().has(BrAPIAdditionalInfoFields.OBS_UNIT_ID));
            assertEquals(expected.get(Columns.OBS_UNIT_ID), sample.get().getAdditionalInfo().get(BrAPIAdditionalInfoFields.OBS_UNIT_ID).getAsString());
        } else {
            assertEquals(String.valueOf(expected.get(Columns.GERMPLASM_GID)), sample.get().getAdditionalInfo().get(BrAPIAdditionalInfoFields.GID).getAsString());
        }

        return ret;
    }

    private Program createProgram(String name, String abbv, String key, String referenceSource, List<BrAPIGermplasm> germplasm, List<Trait> traits) throws ApiException, DoesNotExistException, ValidatorException, BadRequestException {
        SpeciesEntity validSpecies = speciesDAO.findAll().get(0);
        SpeciesRequest speciesRequest = SpeciesRequest.builder()
                                                      .commonName(validSpecies.getCommonName())
                                                      .id(validSpecies.getId())
                                                      .build();
        ProgramRequest programRequest1 = ProgramRequest.builder()
                                                       .name(name)
                                                       .abbreviation(abbv)
                                                       .documentationUrl("localhost:8080")
                                                       .objective("To test things")
                                                       .species(speciesRequest)
                                                       .key(key)
                                                       .build();


        TestUtils.insertAndFetchTestProgram(gson, client, programRequest1);

        // Get main program
        Program program = programService.getByKey(key).get();

        dsl.execute(securityFp.get("InsertProgramRolesBreeder"), testUser.getId().toString(), program.getId().toString());

        if(germplasm != null && !germplasm.isEmpty()) {
            BrAPIExternalReference newReference = new BrAPIExternalReference();
            newReference.setReferenceSource(String.format("%s/programs", referenceSource));
            newReference.setReferenceID(program.getId().toString());

            germplasm.forEach(germ -> germ.getExternalReferences().add(newReference));

            germplasmDAO.createBrAPIGermplasm(germplasm, program.getId(), null);
        }

        if(traits != null && !traits.isEmpty()) {
            AuthenticatedUser user = new AuthenticatedUser(testUser.getName(), new ArrayList<>(), testUser.getId(), new ArrayList<>());
            try {
                ontologyService.createTraits(program.getId(), traits, user, false);
            } catch (ValidatorException e) {
                System.err.println(e.getErrors());
                throw e;
            }
        }

        return program;
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

    private void createUploadAndVerifyFailure(Program program, Map<String, Object> base, String columnToRemove, boolean commit) throws IOException, InterruptedException {
        Map<String, Object> invalidRow = new HashMap<>(base);
        invalidRow.remove(columnToRemove);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(invalidRow)), columnToRemove, commit);
    }

    private JsonObject uploadAndVerifyFailure(Program program, File file, String expectedColumnError, boolean commit) throws InterruptedException, IOException {
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, Map.of(SUBMISSION_NAME, "test-"+UUID.randomUUID()), true, client, program, mappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());

        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, program, mappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + result);

        JsonArray rowErrors = result.getAsJsonObject("progress").getAsJsonArray("rowErrors");
        assertEquals(1, rowErrors.size());
        JsonArray fieldErrors = rowErrors.get(0).getAsJsonObject().getAsJsonArray("errors");
        assertEquals(1, fieldErrors.size());
        JsonObject error = fieldErrors.get(0).getAsJsonObject();
        assertEquals(expectedColumnError, error.get("field").getAsString());
        assertEquals(422, error.get("httpStatusCode").getAsInt());

        return result;
    }

    public File writeDataToFile(List<Map<String, Object>> data) throws IOException {
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

    private String createExperiment(Program program) throws IOException, InterruptedException {
        Flowable<HttpResponse<String>> call = client.exchange(
                GET("/import/mappings?importName=ExperimentsTemplateMap").cookie(new NettyCookie("phylo-token", "test-registered-user")), String.class
        );
        HttpResponse<String> response = call.blockingFirst();
        String expMappingId = JsonParser.parseString(response.body()).getAsJsonObject()
                .getAsJsonObject("result")
                .getAsJsonArray("data")
                .get(0).getAsJsonObject().get("id").getAsString();

        JsonObject importResult = importTestUtils.uploadAndFetch(
                importTestUtils.writeExperimentDataToFile(List.of(makeExpImportRow("Env1")), null),
                null,
                true,
                client,
                program,
                expMappingId);
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

}
