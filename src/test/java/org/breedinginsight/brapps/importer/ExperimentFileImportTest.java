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
import com.google.gson.reflect.TypeToken;
import io.kowalski.fannypack.FannyPack;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.reactivex.Flowable;
import lombok.SneakyThrows;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPISeason;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.brapi.v2.model.pheno.BrAPIPositionCoordinateTypeEnum;
import org.breedinginsight.BrAPITest;
import org.breedinginsight.TestUtils;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.*;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation.Columns;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.ProgramLocationService;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.SpeciesService;
import org.breedinginsight.services.exceptions.BadRequestException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.junit.platform.commons.util.StringUtils;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExperimentFileImportTest extends BrAPITest {

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
    private ProgramDAO programDAO;

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

    private Gson gson = new GsonBuilder().registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>)
                                                 (json, type, context) -> OffsetDateTime.parse(json.getAsString()))
                                         .create();

    @BeforeAll
    public void setup() {
        importTestUtils = new ImportTestUtils();
        Map<String, Object> setupObjects = importTestUtils.setup(client, gson, dsl, speciesService, userDAO, super.getBrapiDsl(), "ExperimentsTemplateMap");
        mappingId = (String) setupObjects.get("mappingId");
        testUser = (BiUserEntity) setupObjects.get("testUser");
        securityFp = (FannyPack) setupObjects.get("securityFp");

    }

    /*
    Tests
    - new experiment
    - existing experiment, new env
    - new env, existing location
    - new experiment, missing required cols (check all required cols)
    - new env, missing required cols (check all req cols)
    - new exp/env with observations
    - new exp/env with invalid observations
    - existing env with missing OU ID
    - existing env with new observations
    - existing env that already has obs
     */

    @Test
    @SneakyThrows
    public void importNewExpNewLocNoObsSuccess() {
        Program program = createProgram("New Exp and Loc", "NEXPL", "NEXPL", BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);
        Map<String, Object> validRow = new HashMap<>();
        validRow.put(Columns.GERMPLASM_GID, "1");
        validRow.put(Columns.TEST_CHECK, "T");
        validRow.put(Columns.EXP_TITLE, "Test Exp");
        validRow.put(Columns.EXP_DESCRIPTION, "Test Description");
        validRow.put(Columns.EXP_UNIT, "Plot");
        validRow.put(Columns.EXP_TYPE, "Phenotyping");
        validRow.put(Columns.ENV, "Test Env");
        validRow.put(Columns.ENV_LOCATION, "Location A");
        validRow.put(Columns.ENV_YEAR, "2023");
        validRow.put(Columns.EXP_UNIT_ID, "a-1");
        validRow.put(Columns.REP_NUM, "1");
        validRow.put(Columns.BLOCK_NUM, "1");
        validRow.put(Columns.ROW, "1");
        validRow.put(Columns.COLUMN, "1");
        validRow.put(Columns.TREATMENT_FACTORS, "Test treatment factors");

        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(writeDataToFile(List.of(validRow), null), null, true, client, program, mappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, program, mappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(200, result.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + result);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("NEW", row.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(validRow, program, null);
    }

    @Test
    @SneakyThrows
    public void importNewEnvExistingExpNoObsSuccess() {
        Program program = createProgram("New Env Existing Exp", "NEWENV", "NEWENV", BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);
        Map<String, Object> newExp = new HashMap<>();
        newExp.put(Columns.GERMPLASM_GID, "1");
        newExp.put(Columns.TEST_CHECK, "T");
        newExp.put(Columns.EXP_TITLE, "Test Exp");
        newExp.put(Columns.EXP_UNIT, "Plot");
        newExp.put(Columns.EXP_TYPE, "Phenotyping");
        newExp.put(Columns.ENV, "New Env");
        newExp.put(Columns.ENV_LOCATION, "Location A");
        newExp.put(Columns.ENV_YEAR, "2023");
        newExp.put(Columns.EXP_UNIT_ID, "a-1");
        newExp.put(Columns.REP_NUM, "1");
        newExp.put(Columns.BLOCK_NUM, "1");
        newExp.put(Columns.ROW, "1");
        newExp.put(Columns.COLUMN, "1");

        importTestUtils.uploadAndFetch(writeDataToFile(List.of(newExp), null), null, true, client, program, mappingId);

        Map<String, Object> newEnv = new HashMap<>();
        newEnv.put(Columns.GERMPLASM_GID, "1");
        newEnv.put(Columns.TEST_CHECK, "T");
        newEnv.put(Columns.EXP_TITLE, "Test Exp");
        newEnv.put(Columns.EXP_UNIT, "Plot");
        newEnv.put(Columns.EXP_TYPE, "Phenotyping");
        newEnv.put(Columns.ENV, "New Trial Existing Exp");
        newEnv.put(Columns.ENV_LOCATION, "Location A");
        newEnv.put(Columns.ENV_YEAR, "2023");
        newEnv.put(Columns.EXP_UNIT_ID, "a-1");
        newEnv.put(Columns.REP_NUM, "1");
        newEnv.put(Columns.BLOCK_NUM, "1");
        newEnv.put(Columns.ROW, "1");
        newEnv.put(Columns.COLUMN, "1");

        JsonObject result = importTestUtils.uploadAndFetch(writeDataToFile(List.of(newEnv), null), null, true, client, program, mappingId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("EXISTING", row.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(newEnv, program, null);
    }

    @Test
    @SneakyThrows
    public void verifyMissingDataThrowsError() {
        Program program = createProgram("Missing Req Cols", "MISS", "MISS", BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);

        Map<String, Object> base = new HashMap<>();
        base.put(Columns.GERMPLASM_GID, "1");
        base.put(Columns.TEST_CHECK, "T");
        base.put(Columns.EXP_TITLE, "Missing Req Cols");
        base.put(Columns.EXP_UNIT, "Plot");
        base.put(Columns.EXP_TYPE, "Phenotyping");
        base.put(Columns.ENV, "Missing GID");
        base.put(Columns.ENV_LOCATION, "Location A");
        base.put(Columns.ENV_YEAR, "2023");
        base.put(Columns.EXP_UNIT_ID, "a-1");
        base.put(Columns.REP_NUM, "1");
        base.put(Columns.BLOCK_NUM, "1");

        Map<String, Object> noGID = new HashMap<>(base);
        noGID.remove(Columns.GERMPLASM_GID);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noGID), null), Columns.GERMPLASM_GID);

        Map<String, Object> noExpTitle = new HashMap<>(base);
        noExpTitle.remove(Columns.EXP_TITLE);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noExpTitle), null), Columns.EXP_TITLE);

        Map<String, Object> noExpUnit = new HashMap<>(base);
        noExpUnit.remove(Columns.EXP_UNIT);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noExpUnit), null), Columns.EXP_UNIT);

        Map<String, Object> noExpType = new HashMap<>(base);
        noExpType.remove(Columns.EXP_TYPE);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noExpType), null), Columns.EXP_TYPE);

        Map<String, Object> noEnv = new HashMap<>(base);
        noEnv.remove(Columns.ENV);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noEnv), null), Columns.ENV);

        Map<String, Object> noEnvLoc = new HashMap<>(base);
        noEnvLoc.remove(Columns.ENV_LOCATION);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noEnvLoc), null), Columns.ENV_LOCATION);

        Map<String, Object> noExpUnitId = new HashMap<>(base);
        noExpUnitId.remove(Columns.EXP_UNIT_ID);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noExpUnitId), null), Columns.EXP_UNIT_ID);

        Map<String, Object> noExpRep = new HashMap<>(base);
        noExpRep.remove(Columns.REP_NUM);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noExpRep), null), Columns.REP_NUM);

        Map<String, Object> noExpBlock = new HashMap<>(base);
        noExpBlock.remove(Columns.BLOCK_NUM);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noExpBlock), null), Columns.BLOCK_NUM);

        Map<String, Object> noEnvYear = new HashMap<>(base);
        noEnvYear.remove(Columns.ENV_YEAR);
        uploadAndVerifyFailure(program, writeDataToFile(List.of(noEnvYear), null), Columns.ENV_YEAR);
    }

    @Test
    @SneakyThrows
    public void importNewExpWithObs() {
        List<Trait> traits = createTraits(1);
        Program program = createProgram("New Exp with Observations", "EXPOBS", "EXPOBS", BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
        Map<String, Object> newExp = new HashMap<>();
        newExp.put(Columns.GERMPLASM_GID, "1");
        newExp.put(Columns.TEST_CHECK, "T");
        newExp.put(Columns.EXP_TITLE, "Test Exp");
        newExp.put(Columns.EXP_UNIT, "Plot");
        newExp.put(Columns.EXP_TYPE, "Phenotyping");
        newExp.put(Columns.ENV, "New Env");
        newExp.put(Columns.ENV_LOCATION, "Location A");
        newExp.put(Columns.ENV_YEAR, "2023");
        newExp.put(Columns.EXP_UNIT_ID, "a-1");
        newExp.put(Columns.REP_NUM, "1");
        newExp.put(Columns.BLOCK_NUM, "1");
        newExp.put(Columns.ROW, "1");
        newExp.put(Columns.COLUMN, "1");
        newExp.put(traits.get(0).getObservationVariableName(), "1");

        JsonObject result = importTestUtils.uploadAndFetch(writeDataToFile(List.of(newExp), traits), null, true, client, program, mappingId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("NEW", row.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(newExp, program, traits);
    }

    @Test
    @SneakyThrows
    public void verifyFailureImportNewExpWithInvalidObs() {
        List<Trait> traits = createTraits(1);
        Program program = createProgram("Invalid Observations", "INVOBS", "INVOBS", BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
        Map<String, Object> newExp = new HashMap<>();
        newExp.put(Columns.GERMPLASM_GID, "1");
        newExp.put(Columns.TEST_CHECK, "T");
        newExp.put(Columns.EXP_TITLE, "Test Exp");
        newExp.put(Columns.EXP_UNIT, "Plot");
        newExp.put(Columns.EXP_TYPE, "Phenotyping");
        newExp.put(Columns.ENV, "New Env");
        newExp.put(Columns.ENV_LOCATION, "Location A");
        newExp.put(Columns.ENV_YEAR, "2023");
        newExp.put(Columns.EXP_UNIT_ID, "a-1");
        newExp.put(Columns.REP_NUM, "1");
        newExp.put(Columns.BLOCK_NUM, "1");
        newExp.put(Columns.ROW, "1");
        newExp.put(Columns.COLUMN, "1");
        newExp.put(traits.get(0).getObservationVariableName(), "Red");

        uploadAndVerifyFailure(program, writeDataToFile(List.of(newExp), traits), traits.get(0).getObservationVariableName());
    }

    @Test
    @SneakyThrows
    public void verifyFailureNewOuExistingEnv() {
        Program program = createProgram("New OU Exising Env", "FAILOU", "FAILOU", BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);
        Map<String, Object> newExp = new HashMap<>();
        newExp.put(Columns.GERMPLASM_GID, "1");
        newExp.put(Columns.TEST_CHECK, "T");
        newExp.put(Columns.EXP_TITLE, "Test Exp");
        newExp.put(Columns.EXP_UNIT, "Plot");
        newExp.put(Columns.EXP_TYPE, "Phenotyping");
        newExp.put(Columns.ENV, "New Env");
        newExp.put(Columns.ENV_LOCATION, "Location A");
        newExp.put(Columns.ENV_YEAR, "2023");
        newExp.put(Columns.EXP_UNIT_ID, "a-1");
        newExp.put(Columns.REP_NUM, "1");
        newExp.put(Columns.BLOCK_NUM, "1");
        newExp.put(Columns.ROW, "1");
        newExp.put(Columns.COLUMN, "1");

        importTestUtils.uploadAndFetch(writeDataToFile(List.of(newExp), null), null, true, client, program, mappingId);

        Map<String, Object> newOU = new HashMap<>(newExp);
        newOU.put(Columns.EXP_UNIT_ID, "a-2");
        newOU.put(Columns.ROW, "1");
        newOU.put(Columns.COLUMN, "2");

        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(writeDataToFile(List.of(newOU), null), null, true, client, program, mappingId);
        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());

        String importId = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").get("importId").getAsString();

        HttpResponse<String> upload = importTestUtils.getUploadedFile(importId, client, program, mappingId);
        JsonObject result = JsonParser.parseString(upload.body()).getAsJsonObject().getAsJsonObject("result");
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + result);

        assertTrue(result.getAsJsonObject("progress").get("message").getAsString().startsWith("Experiment Units are missing Observation Unit Id."));
    }

    @Test
    @SneakyThrows
    public void importNewObsExisingOu() {
        List<Trait> traits = createTraits(1);
        Program program = createProgram("New Obs Existing OU", "OUOBS", "OUOBS", BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
        Map<String, Object> newExp = new HashMap<>();
        newExp.put(Columns.GERMPLASM_GID, "1");
        newExp.put(Columns.TEST_CHECK, "T");
        newExp.put(Columns.EXP_TITLE, "Test Exp");
        newExp.put(Columns.EXP_UNIT, "Plot");
        newExp.put(Columns.EXP_TYPE, "Phenotyping");
        newExp.put(Columns.ENV, "New Env");
        newExp.put(Columns.ENV_LOCATION, "Location A");
        newExp.put(Columns.ENV_YEAR, "2023");
        newExp.put(Columns.EXP_UNIT_ID, "a-1");
        newExp.put(Columns.REP_NUM, "1");
        newExp.put(Columns.BLOCK_NUM, "1");
        newExp.put(Columns.ROW, "1");
        newExp.put(Columns.COLUMN, "1");

        importTestUtils.uploadAndFetch(writeDataToFile(List.of(newExp), null), null, true, client, program, mappingId);

        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of(Utilities.appendProgramKey((String)newExp.get(Columns.EXP_TITLE), program.getKey())), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceID()), program).get(0);

        BrAPIObservationUnit ou = ouDAO.getObservationUnitsForStudyDbId(brAPIStudy.getStudyDbId(), program).get(0);
        Optional<BrAPIExternalReference> ouIdXref = Utilities.getExternalReference(ou.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName()));
        assertTrue(ouIdXref.isPresent());

        Map<String, Object> newObservation = new HashMap<>();
        newObservation.put(Columns.GERMPLASM_GID, "1");
        newObservation.put(Columns.TEST_CHECK, "T");
        newObservation.put(Columns.EXP_TITLE, "Test Exp");
        newObservation.put(Columns.EXP_UNIT, "Plot");
        newObservation.put(Columns.EXP_TYPE, "Phenotyping");
        newObservation.put(Columns.ENV, "New Env");
        newObservation.put(Columns.ENV_LOCATION, "Location A");
        newObservation.put(Columns.ENV_YEAR, "2023");
        newObservation.put(Columns.EXP_UNIT_ID, "a-1");
        newObservation.put(Columns.REP_NUM, "1");
        newObservation.put(Columns.BLOCK_NUM, "1");
        newObservation.put(Columns.ROW, "1");
        newObservation.put(Columns.COLUMN, "1");
        newObservation.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceID());
        newObservation.put(traits.get(0).getObservationVariableName(), "1");

        JsonObject result = importTestUtils.uploadAndFetch(writeDataToFile(List.of(newObservation), traits), null, true, client, program, mappingId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("EXISTING", row.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(newObservation, program, traits);
    }

    @Test
    @SneakyThrows
    public void verifyFailureImportNewObsExisingOuWithExistingObs() {
        List<Trait> traits = createTraits(1);
        Program program = createProgram("New Obs Existing Obs", "EXOBS", "EXOBS", BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
        Map<String, Object> newExp = new HashMap<>();
        newExp.put(Columns.GERMPLASM_GID, "1");
        newExp.put(Columns.TEST_CHECK, "T");
        newExp.put(Columns.EXP_TITLE, "Test Exp");
        newExp.put(Columns.EXP_UNIT, "Plot");
        newExp.put(Columns.EXP_TYPE, "Phenotyping");
        newExp.put(Columns.ENV, "New Env");
        newExp.put(Columns.ENV_LOCATION, "Location A");
        newExp.put(Columns.ENV_YEAR, "2023");
        newExp.put(Columns.EXP_UNIT_ID, "a-1");
        newExp.put(Columns.REP_NUM, "1");
        newExp.put(Columns.BLOCK_NUM, "1");
        newExp.put(Columns.ROW, "1");
        newExp.put(Columns.COLUMN, "1");
        newExp.put(traits.get(0).getObservationVariableName(), "1");

        importTestUtils.uploadAndFetch(writeDataToFile(List.of(newExp), traits), null, true, client, program, mappingId);

        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of(Utilities.appendProgramKey((String)newExp.get(Columns.EXP_TITLE), program.getKey())), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceID()), program).get(0);

        BrAPIObservationUnit ou = ouDAO.getObservationUnitsForStudyDbId(brAPIStudy.getStudyDbId(), program).get(0);
        Optional<BrAPIExternalReference> ouIdXref = Utilities.getExternalReference(ou.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName()));
        assertTrue(ouIdXref.isPresent());

        Map<String, Object> newObservation = new HashMap<>();
        newObservation.put(Columns.GERMPLASM_GID, "1");
        newObservation.put(Columns.TEST_CHECK, "T");
        newObservation.put(Columns.EXP_TITLE, "Test Exp");
        newObservation.put(Columns.EXP_UNIT, "Plot");
        newObservation.put(Columns.EXP_TYPE, "Phenotyping");
        newObservation.put(Columns.ENV, "New Env");
        newObservation.put(Columns.ENV_LOCATION, "Location A");
        newObservation.put(Columns.ENV_YEAR, "2023");
        newObservation.put(Columns.EXP_UNIT_ID, "a-1");
        newObservation.put(Columns.REP_NUM, "1");
        newObservation.put(Columns.BLOCK_NUM, "1");
        newObservation.put(Columns.ROW, "1");
        newObservation.put(Columns.COLUMN, "1");
        newObservation.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceID());
        newObservation.put(traits.get(0).getObservationVariableName(), "2");

        uploadAndVerifyFailure(program, writeDataToFile(List.of(newObservation), traits), traits.get(0).getObservationVariableName());
    }

    private Map<String, Object> assertRowSaved(Map<String, Object> expected, Program program, List<Trait> traits) throws ApiException {
        Map<String, Object> ret = new HashMap<>();

        List<BrAPITrial> trials = brAPITrialDAO.getTrialsByName(List.of(Utilities.appendProgramKey((String)expected.get(Columns.EXP_TITLE), program.getKey())), program);
        assertFalse(trials.isEmpty());
        BrAPITrial trial = trials.get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(trial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());

        List<BrAPIStudy> studies = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceID()), program);
        assertFalse(studies.isEmpty());
        BrAPIStudy study = null;
        for(BrAPIStudy s : studies) {
            if(expected.get(Columns.ENV).equals(Utilities.removeProgramKeyAndUnknownAdditionalData(s.getStudyName(), program.getKey()))) {
                study = s;
                break;
            }
        }
        assertNotNull(study, "Could not find study by name: " + expected.get(Columns.ENV));

        BrAPIObservationUnit ou = ouDAO.getObservationUnitsForStudyDbId(study.getStudyDbId(), program).get(0);
        Optional<BrAPIExternalReference> ouIdXref = Utilities.getExternalReference(ou.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName()));
        assertTrue(ouIdXref.isPresent());

        List<ProgramLocation> locations = locationService.getLocationsByDbId(List.of(study.getLocationDbId()), program.getId());
        assertFalse(locations.isEmpty());
        ProgramLocation location = locations.get(0);

        List<BrAPIGermplasm> germplasms = germplasmDAO.getGermplasmsByDBID(List.of(ou.getGermplasmDbId()), program.getId());
        assertFalse(germplasms.isEmpty());
        BrAPIGermplasm germplasm = germplasms.get(0);

        BrAPISeason season = seasonDAO.getSeasonById(study.getSeasons().get(0), program.getId());

        ret.put("trial", trial);
        ret.put("study", study);
        ret.put("location", location);
        ret.put("observationUnit", ou);
        ret.put("germplasm", germplasm);

        List<BrAPIObservation> observations = null;
        if(traits != null) {
            observations = observationDAO.getObservationsByStudyName(List.of(study.getStudyName()), program);
            assertFalse(observations.isEmpty());

            ret.put("observations", observations);
        }

        assertNotNull(germplasm.getGermplasmName());
        assertEquals(expected.get(Columns.GERMPLASM_GID), germplasm.getAccessionNumber());
        if(expected.containsKey(Columns.TEST_CHECK) && StringUtils.isNotBlank((String)expected.get(Columns.TEST_CHECK))) {
            assertEquals(expected.get(Columns.TEST_CHECK),
                         ou.getObservationUnitPosition()
                           .getEntryType()
                           .name()
                           .substring(0, 1));
        }
        assertEquals(expected.get(Columns.EXP_TITLE), Utilities.removeProgramKey(trial.getTrialName(), program.getKey()));
        assertEquals(expected.get(Columns.EXP_TITLE), Utilities.removeProgramKey(study.getTrialName(), program.getKey()));
        assertEquals(expected.get(Columns.EXP_DESCRIPTION), trial.getTrialDescription());
        assertEquals(expected.get(Columns.EXP_UNIT), trial.getAdditionalInfo().get(BrAPIAdditionalInfoFields.DEFAULT_OBSERVATION_LEVEL).getAsString());
        assertEquals(expected.get(Columns.EXP_UNIT), ou.getAdditionalInfo().get(BrAPIAdditionalInfoFields.OBSERVATION_LEVEL).getAsString());
        assertEquals(expected.get(Columns.EXP_TYPE), trial.getAdditionalInfo().get(BrAPIAdditionalInfoFields.EXPERIMENT_TYPE).getAsString());
        assertEquals(expected.get(Columns.EXP_TYPE), study.getStudyType());
        assertEquals(expected.get(Columns.ENV), Utilities.removeProgramKeyAndUnknownAdditionalData(study.getStudyName(), program.getKey()));
        assertEquals(expected.get(Columns.ENV_LOCATION), Utilities.removeProgramKey(study.getLocationName(), program.getKey()));
        assertEquals(expected.get(Columns.ENV_LOCATION), Utilities.removeProgramKey(location.getName(), program.getKey()));
        assertEquals(expected.get(Columns.ENV_YEAR), season.getSeasonName());
        assertEquals(expected.get(Columns.EXP_UNIT_ID), Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()));

        BrAPIObservationUnitLevelRelationship rep = null;
        BrAPIObservationUnitLevelRelationship block = null;
        for (BrAPIObservationUnitLevelRelationship rel : ou.getObservationUnitPosition().getObservationLevelRelationships()) {
            if ("rep".equals(rel.getLevelName()) && rep == null) {
                rep = rel;
            } else if ("block".equals(rel.getLevelName()) && block == null) {
                block = rel;
            }
        }
        assertNotNull(rep);
        assertNotNull(block);
        assertEquals(expected.get(Columns.REP_NUM), rep.getLevelCode());
        assertEquals(expected.get(Columns.BLOCK_NUM), block.getLevelCode());
        if(expected.containsKey(Columns.ROW)) {
            assertEquals(expected.get(Columns.ROW), ou.getObservationUnitPosition().getPositionCoordinateX());
            assertEquals(BrAPIPositionCoordinateTypeEnum.GRID_ROW, ou.getObservationUnitPosition().getPositionCoordinateXType());
        }
        if(expected.containsKey(Columns.COLUMN)) {
            assertEquals(expected.get(Columns.COLUMN), ou.getObservationUnitPosition().getPositionCoordinateY());
            assertEquals(BrAPIPositionCoordinateTypeEnum.GRID_COL, ou.getObservationUnitPosition().getPositionCoordinateYType());
        }
        if(expected.containsKey(Columns.TREATMENT_FACTORS) && StringUtils.isNotBlank((String)expected.get(Columns.TREATMENT_FACTORS))) {
            assertEquals(expected.get(Columns.TREATMENT_FACTORS), ou.getTreatments().get(0).getFactor());
        }

        if(traits != null) {
            List<String> expectedVariableObservation = new ArrayList<>();
            List<String> actualVariableObservation = new ArrayList<>();
            observations.forEach(observation -> actualVariableObservation.add(String.format("%s:%s", Utilities.removeProgramKey(observation.getObservationVariableName(), program.getKey()), observation.getValue())));
            for(Trait trait : traits) {
                expectedVariableObservation.add(String.format("%s:%s", trait.getObservationVariableName(), expected.get(trait.getObservationVariableName())));
            }

            assertThat("Missing Variable:Observation combo", actualVariableObservation, containsInAnyOrder(expectedVariableObservation.toArray()));
        }

        return ret;
    }

    private Map<String, Object> assertValidPreviewRow(Map<String, Object> expected, JsonObject actual, Program program, List<Trait> traits) {
        Map<String, Object> ret = new HashMap<>();

        assertNotNull(actual.get("trial"));
        BrAPITrial trial = gson.fromJson(actual.getAsJsonObject("trial").getAsJsonObject("brAPIObject"), BrAPITrial.class);
        ret.put("trial", trial);

        assertNotNull(actual.get("study"));
        BrAPIStudy study = gson.fromJson(actual.getAsJsonObject("study").getAsJsonObject("brAPIObject"), BrAPIStudy.class);
        ret.put("study", study);

        assertNotNull(actual.get("location"));
        ProgramLocation location = gson.fromJson(actual.getAsJsonObject("location").getAsJsonObject("brAPIObject"), ProgramLocation.class);
        ret.put("location", location);

        assertNotNull(actual.get("observationUnit"));
        BrAPIObservationUnit ou = gson.fromJson(actual.getAsJsonObject("observationUnit").getAsJsonObject("brAPIObject"), BrAPIObservationUnit.class);
        ret.put("observationUnit", ou);

        assertNotNull(actual.get("germplasm"));
        BrAPIGermplasm germplasm = gson.fromJson(actual.getAsJsonObject("germplasm").getAsJsonObject("brAPIObject"), BrAPIGermplasm.class);
        ret.put("germplasm", germplasm);

        List<BrAPIObservation> observations = null;
        if(traits != null) {
            assertNotNull(actual.get("observations"));
            observations = gson.fromJson(actual.get("observations"), new TypeToken<List<BrAPIObservation>>(){}.getType());
            ret.put("observations", observations);
        }

        assertNotNull(germplasm.getGermplasmName());
        assertEquals(expected.get(Columns.GERMPLASM_GID), germplasm.getAccessionNumber());
        if(expected.containsKey(Columns.TEST_CHECK) && StringUtils.isNotBlank((String)expected.get(Columns.TEST_CHECK))) {
            assertEquals(expected.get(Columns.TEST_CHECK),
                         ou.getObservationUnitPosition()
                           .getEntryType()
                           .name()
                           .substring(0, 1));
        }
        assertEquals(expected.get(Columns.EXP_TITLE), Utilities.removeProgramKey(trial.getTrialName(), program.getKey()));
        assertEquals(expected.get(Columns.EXP_TITLE), Utilities.removeProgramKey(study.getTrialName(), program.getKey()));
        assertEquals(expected.get(Columns.EXP_DESCRIPTION), trial.getTrialDescription());
        assertEquals(expected.get(Columns.EXP_UNIT), trial.getAdditionalInfo().get(BrAPIAdditionalInfoFields.DEFAULT_OBSERVATION_LEVEL).getAsString());
        assertEquals(expected.get(Columns.EXP_UNIT), ou.getAdditionalInfo().get(BrAPIAdditionalInfoFields.OBSERVATION_LEVEL).getAsString());
        assertEquals(expected.get(Columns.EXP_TYPE), trial.getAdditionalInfo().get(BrAPIAdditionalInfoFields.EXPERIMENT_TYPE).getAsString());
        assertEquals(expected.get(Columns.EXP_TYPE), study.getStudyType());
        assertEquals(expected.get(Columns.ENV), Utilities.removeProgramKeyAndUnknownAdditionalData(study.getStudyName(), program.getKey()));
        assertEquals(expected.get(Columns.ENV_LOCATION), study.getLocationName());
        assertEquals(expected.get(Columns.ENV_LOCATION), location.getName());
        //TODO figure out how to get the actual season value
//        assertEquals(expected.getInt(Columns.ENV_YEAR), Integer.parseInt(study.getSeasons().get(0)));
        assertEquals(expected.get(Columns.EXP_UNIT_ID), Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()));

        BrAPIObservationUnitLevelRelationship rep = null;
        BrAPIObservationUnitLevelRelationship block = null;
        for (BrAPIObservationUnitLevelRelationship rel : ou.getObservationUnitPosition().getObservationLevelRelationships()) {
            if ("rep".equals(rel.getLevelName()) && rep == null) {
                rep = rel;
            } else if ("block".equals(rel.getLevelName()) && block == null) {
                block = rel;
            }
        }
        assertNotNull(rep);
        assertNotNull(block);
        assertEquals(expected.get(Columns.REP_NUM), rep.getLevelCode());
        assertEquals(expected.get(Columns.BLOCK_NUM), block.getLevelCode());
        if(expected.containsKey(Columns.ROW)) {
            assertEquals(expected.get(Columns.ROW), ou.getObservationUnitPosition().getPositionCoordinateX());
            assertEquals(BrAPIPositionCoordinateTypeEnum.GRID_ROW, ou.getObservationUnitPosition().getPositionCoordinateXType());
        }
        if(expected.containsKey(Columns.COLUMN)) {
            assertEquals(expected.get(Columns.COLUMN), ou.getObservationUnitPosition().getPositionCoordinateY());
            assertEquals(BrAPIPositionCoordinateTypeEnum.GRID_COL, ou.getObservationUnitPosition().getPositionCoordinateYType());
        }
        if(expected.containsKey(Columns.TREATMENT_FACTORS) && StringUtils.isNotBlank((String)expected.get(Columns.TREATMENT_FACTORS))) {
            assertEquals(expected.get(Columns.TREATMENT_FACTORS), ou.getTreatments().get(0).getFactor());
        }

        if(traits != null) {
            List<String> expectedVariableObservation = new ArrayList<>();
            List<String> actualVariableObservation = new ArrayList<>();
            observations.forEach(observation -> actualVariableObservation.add(String.format("%s:%s", Utilities.removeProgramKey(observation.getObservationVariableName(), program.getKey()), observation.getValue())));
            for(Trait trait : traits) {
                expectedVariableObservation.add(String.format("%s:%s", trait.getObservationVariableName(), expected.get(trait.getObservationVariableName())));
            }

            assertThat("Missing Variable:Observation combo", actualVariableObservation, containsInAnyOrder(expectedVariableObservation.toArray()));
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

            germplasmDAO.importBrAPIGermplasm(germplasm, program.getId(), null);
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

    private List<Trait> createTraits(int numToCreate) {
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

    private JsonObject uploadAndVerifyFailure(Program program, File file, String expectedColumnError) throws InterruptedException, IOException {
        Flowable<HttpResponse<String>> call = importTestUtils.uploadDataFile(file, null, true, client, program, mappingId);
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

    private File writeDataToFile(List<Map<String, Object>> data, List<Trait> traits) throws IOException {
        File file = File.createTempFile("test", ".csv");

        List<Column> columns = new ArrayList<>();
        columns.add(Column.builder().value(Columns.GERMPLASM_NAME).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.GERMPLASM_GID).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(Columns.TEST_CHECK).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.EXP_TITLE).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.EXP_DESCRIPTION).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.EXP_UNIT).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.EXP_TYPE).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.ENV).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.ENV_LOCATION).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.ENV_YEAR).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(Columns.EXP_UNIT_ID).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.REP_NUM).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(Columns.BLOCK_NUM).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(Columns.ROW).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(Columns.COLUMN).dataType(Column.ColumnDataType.INTEGER).build());
        columns.add(Column.builder().value(Columns.TREATMENT_FACTORS).dataType(Column.ColumnDataType.STRING).build());
        columns.add(Column.builder().value(Columns.OBS_UNIT_ID).dataType(Column.ColumnDataType.STRING).build());

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
