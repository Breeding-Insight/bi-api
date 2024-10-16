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
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.typeAdapters.PaginationTypeAdapter;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrAPIPagination;
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
import org.breedinginsight.brapi.v2.dao.*;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation.Columns;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
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
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.util.StringUtils;
import org.opentest4j.AssertionFailedError;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static io.micronaut.http.HttpRequest.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
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
                                         .registerTypeAdapter(BrAPIPagination.class, new PaginationTypeAdapter())
                                         .create();
    private String newExperimentWorkflowId;
    private String appendOverwriteWorkflowId;


    @BeforeAll
    public void setup() {
        importTestUtils = new ImportTestUtils();
        Map<String, Object> setupObjects = importTestUtils.setup(client, gson, dsl, speciesService, userDAO, super.getBrapiDsl(), "ExperimentsTemplateMap");
        mappingId = (String) setupObjects.get("mappingId");
        testUser = (BiUserEntity) setupObjects.get("testUser");
        securityFp = (FannyPack) setupObjects.get("securityFp");
        newExperimentWorkflowId = importTestUtils.getExperimentWorkflowId(client, 0);
        appendOverwriteWorkflowId = importTestUtils.getExperimentWorkflowId(client, 1);
    }

    /*
    Tests
    - new experiment
    - existing experiment, new env
    - new env, existing location
    - new experiment, missing required cols (check all required cols)
    - new env, missing required cols (check all req cols)
    - new exp/env with observation variables (new dataset)
    - new exp/env with observations
    - new exp/env with invalid observations
    - existing env with missing OU ID
    - existing env with new observations
    - existing env that already has obs
    - existing env that already has observation variables (existing dataset)
     */

    @Test
    @SneakyThrows
    public void importNewExpNewLocNoObsSuccess() {
        log.debug("importNewExpNewLocNoObsSuccess");
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

        JsonObject uploadResponse = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(validRow), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        JsonArray previewRows = uploadResponse.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
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
    public void importNewExpMultiNewEnvSuccess() {
        log.debug("importNewExpMultiNewEnvSucces");
        Program program = createProgram("New Exp and Multi New Env", "MULENV", "MULENV", BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);
        Map<String, Object> firstEnv = new HashMap<>();
        firstEnv.put(Columns.GERMPLASM_GID, "1");
        firstEnv.put(Columns.TEST_CHECK, "T");
        firstEnv.put(Columns.EXP_TITLE, "Test Exp");
        firstEnv.put(Columns.EXP_DESCRIPTION, "Test Description");
        firstEnv.put(Columns.EXP_UNIT, "Plot");
        firstEnv.put(Columns.EXP_TYPE, "Phenotyping");
        firstEnv.put(Columns.ENV, "Test Env A");
        firstEnv.put(Columns.ENV_LOCATION, "Location A");
        firstEnv.put(Columns.ENV_YEAR, "2023");
        firstEnv.put(Columns.EXP_UNIT_ID, "a-1");
        firstEnv.put(Columns.REP_NUM, "1");
        firstEnv.put(Columns.BLOCK_NUM, "1");
        firstEnv.put(Columns.ROW, "1");
        firstEnv.put(Columns.COLUMN, "1");
        firstEnv.put(Columns.TREATMENT_FACTORS, "Test treatment factors");

        Map<String, Object> secondEnv = new HashMap<>();
        secondEnv.put(Columns.GERMPLASM_GID, "1");
        secondEnv.put(Columns.TEST_CHECK, "T");
        secondEnv.put(Columns.EXP_TITLE, "Test Exp");
        secondEnv.put(Columns.EXP_DESCRIPTION, "Test Description");
        secondEnv.put(Columns.EXP_UNIT, "Plot");
        secondEnv.put(Columns.EXP_TYPE, "Phenotyping");
        secondEnv.put(Columns.ENV, "Test Env B");
        secondEnv.put(Columns.ENV_LOCATION, "Location B");
        secondEnv.put(Columns.ENV_YEAR, "2023");
        secondEnv.put(Columns.EXP_UNIT_ID, "b-1");
        secondEnv.put(Columns.REP_NUM, "1");
        secondEnv.put(Columns.BLOCK_NUM, "1");
        secondEnv.put(Columns.ROW, "1");
        secondEnv.put(Columns.COLUMN, "1");
        secondEnv.put(Columns.TREATMENT_FACTORS, "Test treatment factors");

        JsonObject uploadResponse = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(firstEnv, secondEnv), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        JsonArray previewRows = uploadResponse.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(2, previewRows.size());
        JsonObject firstRow = previewRows.get(0).getAsJsonObject();

        assertEquals("NEW", firstRow.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("NEW", firstRow.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", firstRow.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", firstRow.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(firstEnv, program, null);

        JsonObject secondRow = previewRows.get(0).getAsJsonObject();

        assertEquals("NEW", secondRow.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("NEW", secondRow.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", secondRow.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", secondRow.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(secondEnv, program, null);
    }

    @Test
    @SneakyThrows
    public void importExistingExpAndEnvErrorMessage() {
        log.debug("importExistingExpAndEnvErrorMessage");
        Program program = createProgram("New Env Existing Exp", "DUPENV", "DUPENV", BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);
        Map<String, Object> newExp = new HashMap<>();
        newExp.put(Columns.GERMPLASM_GID, "1");
        newExp.put(Columns.TEST_CHECK, "T");
        newExp.put(Columns.EXP_TITLE, "Test Exp");
        newExp.put(Columns.EXP_UNIT, "Plot");
        newExp.put(Columns.EXP_TYPE, "Phenotyping");
        newExp.put(Columns.ENV, "Existing Env");
        newExp.put(Columns.ENV_LOCATION, "Location A");
        newExp.put(Columns.ENV_YEAR, "2023");
        newExp.put(Columns.EXP_UNIT_ID, "a-1");
        newExp.put(Columns.REP_NUM, "1");
        newExp.put(Columns.BLOCK_NUM, "1");
        newExp.put(Columns.ROW, "1");
        newExp.put(Columns.COLUMN, "1");

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        Map<String, Object> dupExp = new HashMap<>();
        dupExp.put(Columns.GERMPLASM_GID, "1");
        dupExp.put(Columns.TEST_CHECK, "T");
        dupExp.put(Columns.EXP_TITLE, "Test Exp");
        dupExp.put(Columns.EXP_UNIT, "Plot");
        dupExp.put(Columns.EXP_TYPE, "Phenotyping");
        dupExp.put(Columns.ENV, "Existing Env");
        dupExp.put(Columns.ENV_LOCATION, "Location A");
        dupExp.put(Columns.ENV_YEAR, "2023");
        dupExp.put(Columns.EXP_UNIT_ID, "a-1");
        dupExp.put(Columns.REP_NUM, "1");
        dupExp.put(Columns.BLOCK_NUM, "1");
        dupExp.put(Columns.ROW, "1");
        dupExp.put(Columns.COLUMN, "1");

        JsonObject expResult = importTestUtils.uploadAndFetchWorkflowNoStatusCheck(importTestUtils.writeExperimentDataToFile(List.of(dupExp), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        assertEquals(422, expResult.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + expResult);
        assertTrue(expResult.getAsJsonObject("progress").get("message").getAsString().startsWith("Experiment Title already exists"));
    }

    @Test
    @SneakyThrows
    public void importNewEnvNoObsSuccess() {
        log.debug("importNewEnvNoObsSuccess");
        Program program = createProgram("New Env", "NEWENV", "NEWENV", BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);

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

        JsonObject uploadResponse = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newEnv), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        JsonArray previewRows = uploadResponse.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("NEW", row.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(newEnv, program, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void verifyMissingDataThrowsError(boolean commit) {
        log.debug("verifyMissingDataThrowsError");
        Program program = createProgram("Missing Req Cols "+(commit ? "C" : "P"), "MISS"+(commit ? "C" : "P"), "MISS"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);

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

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noGID), null), Columns.GERMPLASM_GID, commit, newExperimentWorkflowId);

        Map<String, Object> noExpTitle = new HashMap<>(base);
        noExpTitle.remove(Columns.EXP_TITLE);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noExpTitle), null), Columns.EXP_TITLE, commit, newExperimentWorkflowId);

        Map<String, Object> noExpUnit = new HashMap<>(base);
        noExpUnit.remove(Columns.EXP_UNIT);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noExpUnit), null), Columns.EXP_UNIT, commit, newExperimentWorkflowId);

        Map<String, Object> noExpType = new HashMap<>(base);
        noExpType.remove(Columns.EXP_TYPE);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noExpType), null), Columns.EXP_TYPE, commit, newExperimentWorkflowId);

        Map<String, Object> noEnv = new HashMap<>(base);
        noEnv.remove(Columns.ENV);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noEnv), null), Columns.ENV, commit, newExperimentWorkflowId);

        Map<String, Object> noEnvLoc = new HashMap<>(base);
        noEnvLoc.remove(Columns.ENV_LOCATION);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noEnvLoc), null), Columns.ENV_LOCATION, commit, newExperimentWorkflowId);

        Map<String, Object> noExpUnitId = new HashMap<>(base);
        noExpUnitId.remove(Columns.EXP_UNIT_ID);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noExpUnitId), null), Columns.EXP_UNIT_ID, commit, newExperimentWorkflowId);

        Map<String, Object> noExpRep = new HashMap<>(base);
        noExpRep.remove(Columns.REP_NUM);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noExpRep), null), Columns.REP_NUM, commit, newExperimentWorkflowId);

        Map<String, Object> noExpBlock = new HashMap<>(base);
        noExpBlock.remove(Columns.BLOCK_NUM);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noExpBlock), null), Columns.BLOCK_NUM, commit, newExperimentWorkflowId);

        Map<String, Object> noEnvYear = new HashMap<>(base);
        noEnvYear.remove(Columns.ENV_YEAR);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(noEnvYear), null), Columns.ENV_YEAR, commit, newExperimentWorkflowId);
    }

    @Test
    @SneakyThrows
    public void importNewExpWithObsVar() {
        log.debug("importNewExpWithObsVar");
        List<Trait> traits = importTestUtils.createTraits(1);
        Program program = createProgram("New Exp with Observations Vars", "EXPVRR", "EXPVRR", BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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
        newExp.put(traits.get(0).getObservationVariableName(), null);

        JsonObject result = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();
        JsonArray datasets = row.getAsJsonObject("trial").get("brAPIObject").getAsJsonObject().get("additionalInfo").getAsJsonObject().getAsJsonArray(BrAPIAdditionalInfoFields.DATASETS);

        assertEquals("NEW", row.getAsJsonObject("trial").get("state").getAsString());
        assertFalse(datasets.isEmpty());
        assertTrue(importTestUtils.UUID_REGEX.matcher(datasets.get(0).getAsJsonObject().get("id").getAsString()).matches());
        assertEquals("NEW", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(newExp, program, traits);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void verifyDiffYearSameEnvThrowsError(boolean commit) {
        log.debug("verifyDiffYEarSameEnvThrowsError");
        Program program = createProgram("Diff Years "+(commit ? "C" : "P"), "YEARS"+(commit ? "C" : "P"), "YEARS"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(2), null);

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put(Columns.GERMPLASM_GID, "1");
        row.put(Columns.TEST_CHECK, "T");
        row.put(Columns.EXP_TITLE, "Different Years");
        row.put(Columns.EXP_UNIT, "Plot");
        row.put(Columns.EXP_TYPE, "Phenotyping");
        row.put(Columns.ENV, "Diff Year");
        row.put(Columns.ENV_LOCATION, "Location A");
        row.put(Columns.ENV_YEAR, "2023");
        row.put(Columns.EXP_UNIT_ID, "a-1");
        row.put(Columns.REP_NUM, "1");
        row.put(Columns.BLOCK_NUM, "1");
        rows.add(row);

        row = new HashMap<>();
        row.put(Columns.GERMPLASM_GID, "2");
        row.put(Columns.TEST_CHECK, "T");
        row.put(Columns.EXP_TITLE, "Different Years");
        row.put(Columns.EXP_UNIT, "Plot");
        row.put(Columns.EXP_TYPE, "Phenotyping");
        row.put(Columns.ENV, "Diff Year");
        row.put(Columns.ENV_LOCATION, "Location A");
        row.put(Columns.ENV_YEAR, "2022");
        row.put(Columns.EXP_UNIT_ID, "a-2");
        row.put(Columns.REP_NUM, "1");
        row.put(Columns.BLOCK_NUM, "2");
        rows.add(row);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(rows, null), Columns.ENV_YEAR, commit, newExperimentWorkflowId);

    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void verifyDiffLocSameEnvThrowsError(boolean commit) {
        log.debug("verifyDiffLocSameEnvThrowsError");
        Program program = createProgram("Diff Locations "+(commit ? "C" : "P"), "LOCS"+(commit ? "C" : "P"), "LOCS"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(2), null);

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put(Columns.GERMPLASM_GID, "1");
        row.put(Columns.TEST_CHECK, "T");
        row.put(Columns.EXP_TITLE, "Different Years");
        row.put(Columns.EXP_UNIT, "Plot");
        row.put(Columns.EXP_TYPE, "Phenotyping");
        row.put(Columns.ENV, "Diff Year");
        row.put(Columns.ENV_LOCATION, "Location A");
        row.put(Columns.ENV_YEAR, "2023");
        row.put(Columns.EXP_UNIT_ID, "a-1");
        row.put(Columns.REP_NUM, "1");
        row.put(Columns.BLOCK_NUM, "1");
        rows.add(row);

        row = new HashMap<>();
        row.put(Columns.GERMPLASM_GID, "2");
        row.put(Columns.TEST_CHECK, "T");
        row.put(Columns.EXP_TITLE, "Different Years");
        row.put(Columns.EXP_UNIT, "Plot");
        row.put(Columns.EXP_TYPE, "Phenotyping");
        row.put(Columns.ENV, "Diff Year");
        row.put(Columns.ENV_LOCATION, "Location B");
        row.put(Columns.ENV_YEAR, "2023");
        row.put(Columns.EXP_UNIT_ID, "a-2");
        row.put(Columns.REP_NUM, "1");
        row.put(Columns.BLOCK_NUM, "2");
        rows.add(row);

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(rows, null), Columns.ENV_LOCATION, commit, newExperimentWorkflowId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void importNewExpWithObs(boolean commit) {
        log.debug("importNewExpWithObs");
        List<Trait> traits = importTestUtils.createTraits(1);
        Program program = createProgram("New Exp with Observations "+(commit ? "C" : "P"), "NEXOB"+(commit ? "C" : "P"), "NEXOB"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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

        JsonObject result = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), traits), null, true, client, program, mappingId, newExperimentWorkflowId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("NEW", row.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", row.getAsJsonObject("observationUnit").get("state").getAsString());

        if(commit) {
            assertRowSaved(newExp, program, traits);
        } else {
            assertValidPreviewRow(newExp, row, program, traits);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void verifyFailureImportNewExpWithInvalidObs(boolean commit) {
        log.debug("verifyFailureImportNewExpWithInvalidObs");
        List<Trait> traits = importTestUtils.createTraits(1);
        Program program = createProgram("Invalid Observations "+(commit ? "C" : "P"), "INVOB"+(commit ? "C" : "P"), "INVOB"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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

        uploadAndVerifyWorkflowFailure(program, importTestUtils.writeExperimentDataToFile(List.of(newExp), traits), traits.get(0).getObservationVariableName(), commit, newExperimentWorkflowId);

    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void verifyFailureNewOuExistingEnv(boolean commit) {
        log.debug("verifyFailureNewOuExistingEnv");
        Program program = createProgram("New OU Existing Env "+(commit ? "C" : "P"), "FLOU"+(commit ? "C" : "P"), "FLOU"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), null);
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

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        Map<String, Object> newOU = new HashMap<>(newExp);
        newOU.put(Columns.EXP_UNIT_ID, "a-2");
        newOU.put(Columns.ROW, "1");
        newOU.put(Columns.COLUMN, "2");

        JsonObject result = importTestUtils.uploadAndFetchWorkflowNoStatusCheck(importTestUtils.writeExperimentDataToFile(List.of(newOU), null), null, true, client, program, mappingId, newExperimentWorkflowId);
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + result);

        assertTrue(result.getAsJsonObject("progress").get("message").getAsString().startsWith("Experiment Title already exists"));
    }

    @Test
    @SneakyThrows
    public void importNewObsVarExistingOu() {
        log.debug("importNewObsVarExistingOu");
        List<Trait> traits = importTestUtils.createTraits(2);
        Program program = createProgram("New ObsVar Existing OU", "OUVAR", "OUVAR", BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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
        newExp.put(traits.get(0).getObservationVariableName(), null);

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of((String)newExp.get(Columns.EXP_TITLE)), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceId()), program).get(0);

        BrAPIObservationUnit ou = ouDAO.getObservationUnitsForStudyDbId(brAPIStudy.getStudyDbId(), program).get(0);
        Optional<BrAPIExternalReference> ouIdXref = Utilities.getExternalReference(ou.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName()));
        assertTrue(ouIdXref.isPresent());

        Map<String, Object> newObsVar = new HashMap<>();
        newObsVar.put(Columns.GERMPLASM_GID, "1");
        newObsVar.put(Columns.TEST_CHECK, "T");
        newObsVar.put(Columns.EXP_TITLE, "Test Exp");
        newObsVar.put(Columns.EXP_UNIT, "Plot");
        newObsVar.put(Columns.EXP_TYPE, "Phenotyping");
        newObsVar.put(Columns.ENV, "New Env");
        newObsVar.put(Columns.ENV_LOCATION, "Location A");
        newObsVar.put(Columns.ENV_YEAR, "2023");
        newObsVar.put(Columns.EXP_UNIT_ID, "a-1");
        newObsVar.put(Columns.REP_NUM, "1");
        newObsVar.put(Columns.BLOCK_NUM, "1");
        newObsVar.put(Columns.ROW, "1");
        newObsVar.put(Columns.COLUMN, "1");
        newObsVar.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceId());
        newObsVar.put(traits.get(1).getObservationVariableName(), null);

        JsonObject result = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newObsVar), traits), null, true, client, program, mappingId, appendOverwriteWorkflowId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();
        JsonArray datasets = row.getAsJsonObject("trial").get("brAPIObject").getAsJsonObject().get("additionalInfo").getAsJsonObject().getAsJsonArray(BrAPIAdditionalInfoFields.DATASETS);

        assertEquals("EXISTING", row.getAsJsonObject("trial").get("state").getAsString());
        assertFalse(datasets.isEmpty());
        assertTrue(importTestUtils.UUID_REGEX.matcher(datasets.get(0).getAsJsonObject().get("id").getAsString()).matches());
        assertEquals("EXISTING", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(newObsVar, program, traits);
    }

    @Test
    @SneakyThrows
    public void importNewObsVarByObsUnitId() {
        log.debug("importNewObsVarByObsUnitId");
        List<Trait> traits = importTestUtils.createTraits(2);
        Program program = createProgram("New ObsVar Referring to OU by ID", "OUVAR", "VAROU", BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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
        newExp.put(traits.get(0).getObservationVariableName(), null);

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of((String)newExp.get(Columns.EXP_TITLE)), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceId()), program).get(0);

        BrAPIObservationUnit ou = ouDAO.getObservationUnitsForStudyDbId(brAPIStudy.getStudyDbId(), program).get(0);
        Optional<BrAPIExternalReference> ouIdXref = Utilities.getExternalReference(ou.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName()));
        assertTrue(ouIdXref.isPresent());

        Map<String, Object> newObsVar = new HashMap<>();
        newObsVar.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceId());
        newObsVar.put(traits.get(1).getObservationVariableName(), null);

        JsonObject result = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newObsVar), traits), null, true, client, program, mappingId, appendOverwriteWorkflowId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();
        JsonArray datasets = row.getAsJsonObject("trial").get("brAPIObject").getAsJsonObject().get("additionalInfo").getAsJsonObject().getAsJsonArray(BrAPIAdditionalInfoFields.DATASETS);

        assertEquals("EXISTING", row.getAsJsonObject("trial").get("state").getAsString());
        assertFalse(datasets.isEmpty());
        assertTrue(importTestUtils.UUID_REGEX.matcher(datasets.get(0).getAsJsonObject().get("id").getAsString()).matches());
        assertEquals("EXISTING", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowReferencedByOUIdSaved(newObsVar, program, traits);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void importNewObservationDataByObsUnitId(boolean commit) {
        log.debug("importNewObservationDataByObsUnitId");
        List<Trait> traits = importTestUtils.createTraits(1);
        Program program = createProgram("New Observation Referring to OU by ID"+(commit ? "C" : "P"), "OUDAT"+(commit ? "C" : "P"), "DATOU"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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
        newExp.put(traits.get(0).getObservationVariableName(), null);   // empty dataset

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of((String)newExp.get(Columns.EXP_TITLE)), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceId()), program).get(0);

        BrAPIObservationUnit ou = ouDAO.getObservationUnitsForStudyDbId(brAPIStudy.getStudyDbId(), program).get(0);
        Optional<BrAPIExternalReference> ouIdXref = Utilities.getExternalReference(ou.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName()));
        assertTrue(ouIdXref.isPresent());

        Map<String, Object> newObsVar = new HashMap<>();
        newObsVar.put(Columns.GERMPLASM_GID, "1");
        newObsVar.put(Columns.TEST_CHECK, "T");
        newObsVar.put(Columns.EXP_TITLE, "Test Exp");
        newObsVar.put(Columns.EXP_UNIT, "Plot");
        newObsVar.put(Columns.EXP_TYPE, "Phenotyping");
        newObsVar.put(Columns.ENV, "New Env");
        newObsVar.put(Columns.ENV_LOCATION, "Location A");
        newObsVar.put(Columns.ENV_YEAR, "2023");
        newObsVar.put(Columns.EXP_UNIT_ID, "a-1");
        newObsVar.put(Columns.REP_NUM, "1");
        newObsVar.put(Columns.BLOCK_NUM, "1");
        newObsVar.put(Columns.ROW, "1");
        newObsVar.put(Columns.COLUMN, "1");
        newObsVar.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceId());
        newObsVar.put(traits.get(0).getObservationVariableName(), "1");

        JsonObject result = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newObsVar), traits), null, commit, client, program, mappingId, appendOverwriteWorkflowId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();
        JsonArray datasets = row.getAsJsonObject("trial").get("brAPIObject").getAsJsonObject().get("additionalInfo").getAsJsonObject().getAsJsonArray(BrAPIAdditionalInfoFields.DATASETS);

        assertEquals("EXISTING", row.getAsJsonObject("trial").get("state").getAsString());
        assertFalse(datasets.isEmpty());
        assertTrue(importTestUtils.UUID_REGEX.matcher(datasets.get(0).getAsJsonObject().get("id").getAsString()).matches());
        assertEquals("EXISTING", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("observationUnit").get("state").getAsString());
        if(commit) {
            assertRowSaved(newObsVar, program, traits);
        } else {
            assertValidPreviewRow(newObsVar, row, program, traits);
        }
    }

    /*
    Scenario:
    - an experiment was created with observations
    - an overwrite operation is attempted with blank observation values
    - verify blank observation values do not overwrite original values
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void verifyBlankObsInOverwriteIsNoOp(boolean commit) {
        List<Trait> traits = importTestUtils.createTraits(1);
        Program program = createProgram("Overwrite Attempt With Blank Obs"+(commit ? "C" : "P"), "NOOP"+(commit ? "C" : "P"), "NOOP"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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
        newExp.put(traits.get(0).getObservationVariableName(), "1");   // Valid observation value.

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), traits), null, true, client, program, mappingId, newExperimentWorkflowId);

        // Fetch the ObsUnitId to use in the overwrite upload.
        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of((String)newExp.get(Columns.EXP_TITLE)), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceId()), program).get(0);
        BrAPIObservationUnit ou = ouDAO.getObservationUnitsForStudyDbId(brAPIStudy.getStudyDbId(), program).get(0);
        Optional<BrAPIExternalReference> ouIdXref = Utilities.getExternalReference(ou.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName()));
        assertTrue(ouIdXref.isPresent());

        assertRowSaved(newExp, program, traits);

        Map<String, Object> newObsVar = new HashMap<>();
        newObsVar.put(Columns.GERMPLASM_GID, "1");
        newObsVar.put(Columns.TEST_CHECK, "T");
        newObsVar.put(Columns.EXP_TITLE, "Test Exp");
        newObsVar.put(Columns.EXP_UNIT, "Plot");
        newObsVar.put(Columns.EXP_TYPE, "Phenotyping");
        newObsVar.put(Columns.ENV, "New Env");
        newObsVar.put(Columns.ENV_LOCATION, "Location A");
        newObsVar.put(Columns.ENV_YEAR, "2023");
        newObsVar.put(Columns.EXP_UNIT_ID, "a-1");
        newObsVar.put(Columns.REP_NUM, "1");
        newObsVar.put(Columns.BLOCK_NUM, "1");
        newObsVar.put(Columns.ROW, "1");
        newObsVar.put(Columns.COLUMN, "1");
        newObsVar.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceId());  // Indicates this is an overwrite.
        newObsVar.put(traits.get(0).getObservationVariableName(), "");  // Empty string should be no op.

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("overwrite", "true");
        requestBody.put("overwriteReason", "testing");

        JsonObject result = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newObsVar), traits), requestBody, commit, client, program, mappingId, appendOverwriteWorkflowId);
        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        // Verify that the overwrite attempt with blank observation value did not overwrite the original value.
        assertRowSaved(newExp, program, traits);
        if(commit) {
            assertRowSaved(newExp, program, traits);
        } else {
            assertValidPreviewRow(newExp, row, program, traits);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void importNewObsExistingOu(boolean commit) {
        log.debug("importNewObsExistingOu");
        List<Trait> traits = importTestUtils.createTraits(1);
        Program program = createProgram("New Obs Existing OU "+(commit ? "C" : "P"), "OUOBS"+(commit ? "C" : "P"), "OUOBS"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), null), null, true, client, program, mappingId, newExperimentWorkflowId);

        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of((String)newExp.get(Columns.EXP_TITLE)), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceId()), program).get(0);

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
        newObservation.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceId());
        newObservation.put(traits.get(0).getObservationVariableName(), "1");

        JsonObject result = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newObservation), traits), null, commit, client, program, mappingId, appendOverwriteWorkflowId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("EXISTING", row.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("observationUnit").get("state").getAsString());
        if(commit) {
            assertRowSaved(newObservation, program, traits);
        } else {
            assertValidPreviewRow(newObservation, row, program, traits);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void verifyFailureImportNewObsExistingOuWithExistingObs(boolean commit) {
        log.debug("verifyFailureImportNewObsExistingOuWithExistingObs");
        List<Trait> traits = importTestUtils.createTraits(1);
        Program program = createProgram("New Obs Existing Obs "+(commit ? "C" : "P"), "FEXOB"+(commit ? "C" : "P"), "FEXOB"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), traits), null, true, client, program, mappingId, newExperimentWorkflowId);

        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of((String)newExp.get(Columns.EXP_TITLE)), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceId()), program).get(0);

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
        newObservation.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceId());
        newObservation.put(traits.get(0).getObservationVariableName(), "2");
        
        uploadAndVerifyWorkflowFailureNonTabular(program, importTestUtils.writeExperimentDataToFile(List.of(newObservation), traits), traits.get(0).getObservationVariableName(), commit, newExperimentWorkflowId);
    }

    /*
    Scenario:
    - an experiment was created with observations
    - a new experiment is created after the first experiment
    - verify the second experiment gets created successfully
     */
    @Test
    @SneakyThrows
    public void importSecondExpAfterFirstExpWithObs() {
        log.debug("importSecondExpAfterFirstExpWithObs");
        List<Trait> traits = importTestUtils.createTraits(1);
        Program program = createProgram("New Exp After First", "NEAF", "NEAF", BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
        Map<String, Object> newExpA = new HashMap<>();
        newExpA.put(Columns.GERMPLASM_GID, "1");
        newExpA.put(Columns.TEST_CHECK, "T");
        newExpA.put(Columns.EXP_TITLE, "Test Exp A");
        newExpA.put(Columns.EXP_UNIT, "Plot");
        newExpA.put(Columns.EXP_TYPE, "Phenotyping");
        newExpA.put(Columns.ENV, "New Env");
        newExpA.put(Columns.ENV_LOCATION, "Location A");
        newExpA.put(Columns.ENV_YEAR, "2023");
        newExpA.put(Columns.EXP_UNIT_ID, "a-1");
        newExpA.put(Columns.REP_NUM, "1");
        newExpA.put(Columns.BLOCK_NUM, "1");
        newExpA.put(Columns.ROW, "1");
        newExpA.put(Columns.COLUMN, "1");
        newExpA.put(traits.get(0).getObservationVariableName(), "1");

        JsonObject resultA = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExpA), traits), null, true, client, program, mappingId, newExperimentWorkflowId);

        JsonArray previewRowsA = resultA.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRowsA.size());
        JsonObject rowA = previewRowsA.get(0).getAsJsonObject();

        assertEquals("NEW", rowA.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("NEW", rowA.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", rowA.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", rowA.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(newExpA, program, traits);

        Map<String, Object> newExpB = new HashMap<>();
        newExpB.put(Columns.GERMPLASM_GID, "1");
        newExpB.put(Columns.TEST_CHECK, "T");
        newExpB.put(Columns.EXP_TITLE, "Test Exp B");
        newExpB.put(Columns.EXP_UNIT, "Plot");
        newExpB.put(Columns.EXP_TYPE, "Phenotyping");
        newExpB.put(Columns.ENV, "New Env");
        newExpB.put(Columns.ENV_LOCATION, "Location A");
        newExpB.put(Columns.ENV_YEAR, "2023");
        newExpB.put(Columns.EXP_UNIT_ID, "a-1");
        newExpB.put(Columns.REP_NUM, "1");
        newExpB.put(Columns.BLOCK_NUM, "1");
        newExpB.put(Columns.ROW, "1");
        newExpB.put(Columns.COLUMN, "1");
        newExpB.put(traits.get(0).getObservationVariableName(), "1");

        JsonObject resultB = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExpB), traits), null, true, client, program, mappingId, newExperimentWorkflowId);

        JsonArray previewRowsB = resultB.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRowsB.size());
        JsonObject rowB = previewRowsB.get(0).getAsJsonObject();

        assertEquals("NEW", rowB.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("EXISTING", rowB.getAsJsonObject("location").get("state").getAsString());
        assertEquals("NEW", rowB.getAsJsonObject("study").get("state").getAsString());
        assertEquals("NEW", rowB.getAsJsonObject("observationUnit").get("state").getAsString());
        assertRowSaved(newExpB, program, traits);
    }

    /*
    Scenario:
    - an experiment was created with observations
    - do a second upload with additional observations for the experiment
    - verify the second set of observations get uploaded successfully
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void importNewObsAfterFirstExpWithObs(boolean commit) {
        log.debug("importNewObsAfterFirstExpWithObs");
        List<Trait> traits = importTestUtils.createTraits(2);
        Program program = createProgram("Exp with additional Uploads "+(commit ? "C" : "P"), "EXAU"+(commit ? "C" : "P"), "EXAU"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
        Map<String, Object> newExp = new HashMap<>();
        newExp.put(Columns.GERMPLASM_GID, "1");
        newExp.put(Columns.TEST_CHECK, "T");
        newExp.put(Columns.EXP_TITLE, "Test Exp");
        newExp.put(Columns.EXP_UNIT, "Plot");
        newExp.put(Columns.EXP_TYPE, "Phenotyping");
        newExp.put(Columns.ENV, "New Env");
        newExp.put(Columns.ENV_LOCATION, "Location A");
        newExp.put(Columns.ENV_YEAR, "2025");
        newExp.put(Columns.EXP_UNIT_ID, "a-1");
        newExp.put(Columns.REP_NUM, "1");
        newExp.put(Columns.BLOCK_NUM, "1");
        newExp.put(Columns.ROW, "1");
        newExp.put(Columns.COLUMN, "1");
        newExp.put(traits.get(0).getObservationVariableName(), "1");

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), traits), null, true, client, program, mappingId, newExperimentWorkflowId);

        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of((String)newExp.get(Columns.EXP_TITLE)), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceId()), program).get(0);

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
        newObservation.put(Columns.ENV_YEAR, "2025");
        newObservation.put(Columns.EXP_UNIT_ID, "a-1");
        newObservation.put(Columns.REP_NUM, "1");
        newObservation.put(Columns.BLOCK_NUM, "1");
        newObservation.put(Columns.ROW, "1");
        newObservation.put(Columns.COLUMN, "1");
        newObservation.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceId());
        newObservation.put(traits.get(0).getObservationVariableName(), "1");
        newObservation.put(traits.get(1).getObservationVariableName(), "2");

        JsonObject result = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newObservation), traits), null, commit, client, program, mappingId, appendOverwriteWorkflowId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("EXISTING", row.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("observationUnit").get("state").getAsString());
        if(commit) {
            assertRowSaved(newObservation, program, traits);
        } else {
            assertValidPreviewRow(newObservation, row, program, traits);
        }
    }

    /*
    Scenario:
    - Create an experiment with valid observations.
    - Upload a second file with (1) a blank observation, (2) a changed valid observation, and (3) a new observation for the experiment.
    - Verify that (1) the blank observation makes no change, (2) the changed observation is overwritten, and (3) new observations are appended to the experiment.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SneakyThrows
    public void importNewObsAfterFirstExpWithObs_blank(boolean commit) {
        log.debug("importNewObsAfterFirstExpWithObs_blank");
        List<Trait> traits = importTestUtils.createTraits(3);
        Program program = createProgram("Exp with additional Uploads (blank) "+(commit ? "C" : "P"), "EXAUB"+(commit ? "C" : "P"), "EXAUB"+(commit ? "C" : "P"), BRAPI_REFERENCE_SOURCE, createGermplasm(1), traits);
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
        String originalValue = "1";  // Convenience variable, this value is reused.
        newExp.put(traits.get(0).getObservationVariableName(), originalValue);
        newExp.put(traits.get(1).getObservationVariableName(), "2");

        importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newExp), traits), null, true, client, program, mappingId, newExperimentWorkflowId);

        BrAPITrial brAPITrial = brAPITrialDAO.getTrialsByName(List.of((String)newExp.get(Columns.EXP_TITLE)), program).get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(brAPITrial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());
        BrAPIStudy brAPIStudy = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceId()), program).get(0);

        BrAPIObservationUnit ou = ouDAO.getObservationUnitsForStudyDbId(brAPIStudy.getStudyDbId(), program).get(0);
        Optional<BrAPIExternalReference> ouIdXref = Utilities.getExternalReference(ou.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName()));
        assertTrue(ouIdXref.isPresent());

        assertRowSaved(newExp, program, traits);

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
        newObservation.put(Columns.OBS_UNIT_ID, ouIdXref.get().getReferenceId());
        newObservation.put(traits.get(0).getObservationVariableName(), "");    // This blank value should not overwrite.
        newObservation.put(traits.get(1).getObservationVariableName(), "3");   // This valid value should overwrite.
        newObservation.put(traits.get(2).getObservationVariableName(), "4");   // This valid new observation should be appended.


        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("overwrite", "true");
        requestBody.put("overwriteReason", "testing");
        JsonObject result = importTestUtils.uploadAndFetchWorkflow(importTestUtils.writeExperimentDataToFile(List.of(newObservation), traits), requestBody, commit, client, program, mappingId, appendOverwriteWorkflowId);

        JsonArray previewRows = result.get("preview").getAsJsonObject().get("rows").getAsJsonArray();
        assertEquals(1, previewRows.size());
        JsonObject row = previewRows.get(0).getAsJsonObject();

        assertEquals("EXISTING", row.getAsJsonObject("trial").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("location").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("study").get("state").getAsString());
        assertEquals("EXISTING", row.getAsJsonObject("observationUnit").get("state").getAsString());

        // The blank value should not have produced an overwrite.
        // This change is to make the "expected" value correct.
        Map<String, Object> bothPhenotypeObservations = new HashMap<>(newObservation);
        bothPhenotypeObservations.put(traits.get(0).getObservationVariableName(), originalValue);
        if(commit) {
            assertRowSaved(bothPhenotypeObservations, program, traits);
        } else {
            assertValidPreviewRow(bothPhenotypeObservations, row, program, traits);
        }
    }

    private Map<String, Object> assertRowReferencedByOUIdSaved(Map<String, Object> expected, Program program, List<Trait> traits) throws ApiException {
        Map<String, Object> ret = new HashMap<>();

        List<BrAPIObservationUnit> units = ouDAO.getObservationUnitsById(List.of((String)expected.get(Columns.OBS_UNIT_ID)), program);
        assertFalse(units.isEmpty());

        List<BrAPIObservation> observations = null;
        if(traits != null) {
            observations = observationDAO.getObservationsByStudyName(List.of(units.get(0).getStudyName()), program);
            if (expected.get(traits.get(0).getObservationVariableName()) == null) {
                assertTrue(observations.isEmpty());
            } else {
                assertFalse(observations.isEmpty());
                List<String> expectedVariableObservation = new ArrayList<>();
                List<String> actualVariableObservation = new ArrayList<>();
                observations.forEach(observation -> actualVariableObservation.add(String.format("%s:%s", Utilities.removeProgramKey(observation.getObservationVariableName(), program.getKey()), observation.getValue())));
                for(Trait trait : traits) {
                    if (expected.get(trait.getObservationVariableName()) != null) {
                        expectedVariableObservation.add(String.format("%s:%s", trait.getObservationVariableName(), expected.get(trait.getObservationVariableName())));
                    }
                }
                if (actualVariableObservation.isEmpty()) {
                    assertTrue(expectedVariableObservation.isEmpty());
                } else {
                    assertThat("Missing Variable:Observation combo", actualVariableObservation, containsInAnyOrder(expectedVariableObservation.toArray()));
                }

            }

            ret.put("observations", observations);
        }

        return ret;
    }

    private Map<String, Object> assertRowSaved(Map<String, Object> expected, Program program, List<Trait> traits) throws ApiException {
        Map<String, Object> ret = new HashMap<>();

        List<BrAPITrial> trials = brAPITrialDAO.getTrialsByName(List.of((String)expected.get(Columns.EXP_TITLE)), program);
        assertFalse(trials.isEmpty());
        BrAPITrial trial = trials.get(0);
        Optional<BrAPIExternalReference> trialIdXref = Utilities.getExternalReference(trial.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()));
        assertTrue(trialIdXref.isPresent());

        List<BrAPIStudy> studies = brAPIStudyDAO.getStudiesByExperimentID(UUID.fromString(trialIdXref.get().getReferenceId()), program);
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
            if (expected.get(traits.get(0).getObservationVariableName()) == null) {
                assertTrue(observations.isEmpty());
            } else {
                assertFalse(observations.isEmpty());
            }


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
            observations.forEach(observation -> actualVariableObservation.add(
                    String.format(
                            "%s:%s",
                            Utilities.removeProgramKey(observation.getObservationVariableName(), program.getKey()),
                            observation.getValue()
                    )
            ));

            for(Trait trait : traits) {
                if (expected.get(trait.getObservationVariableName()) != null) {
                    expectedVariableObservation.add(String.format("%s:%s", trait.getObservationVariableName(), expected.get(trait.getObservationVariableName())));
                }
            }
            if (actualVariableObservation.isEmpty()) {
                assertTrue(expectedVariableObservation.isEmpty());
            } else {
                assertThat("Missing Variable:Observation combo", actualVariableObservation, containsInAnyOrder(expectedVariableObservation.toArray()));
            }
        }

        return ret;
    }

    private Map<String, Object> assertValidPreviewRow(Map<String, Object> expected, JsonObject actual, Program program, List<Trait> traits) throws ApiException {
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
            observations = StreamSupport.stream(actual.getAsJsonArray("observations")
                            .spliterator(), false)
                    .map(obs -> gson.fromJson(obs.getAsJsonObject()
                            .getAsJsonObject("brAPIObject"), BrAPIObservation.class))
                    .collect(Collectors.toList());
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

        /*
            added this try block because the year can come back as either the seasonDbId
            or the actual year value depending on if the test is appending data to an existing experiment/environment
            or creating a new environment as part of the upload
         */
        try {
            assertEquals(expected.get(Columns.ENV_YEAR), study.getSeasons().get(0));
        } catch (AssertionFailedError error) {
            String expectedYearId = yearToSeasonDbId((String)expected.get(Columns.ENV_YEAR), program.getId());
            assertEquals(expectedYearId, study.getSeasons().get(0));
        }

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

    private String yearToSeasonDbId(String year, UUID programId) throws ApiException {
        List<BrAPISeason> seasons = this.seasonDAO.getSeasonsByYear(year, programId);

        for (BrAPISeason season : seasons) {
            if (null == season.getSeasonName() || season.getSeasonName()
                    .isBlank() || season.getSeasonName()
                    .equals(year)) {
                return season.getSeasonDbId();
            }
        }

        return null;
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

    private JsonObject uploadAndVerifyFailure(Program program, File file, String expectedColumnError, boolean commit) throws InterruptedException, IOException {
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

    private JsonObject uploadAndVerifyWorkflowFailure(Program program, File file, String expectedColumnError, boolean commit, String workflowId) throws InterruptedException, IOException {

        JsonObject result = importTestUtils.uploadAndFetchWorkflowNoStatusCheck(file, null, true, client, program, mappingId, newExperimentWorkflowId);
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

    private JsonObject uploadAndVerifyWorkflowFailureNonTabular(Program program, File file, String expectedColumnError, boolean commit, String workflowId) throws InterruptedException, IOException {

        JsonObject result = importTestUtils.uploadAndFetchWorkflowNoStatusCheck(file, null, true, client, program, mappingId, newExperimentWorkflowId);
        assertEquals(422, result.getAsJsonObject("progress").get("statuscode").getAsInt(), "Returned data: " + result);

        return result;
    }

}
