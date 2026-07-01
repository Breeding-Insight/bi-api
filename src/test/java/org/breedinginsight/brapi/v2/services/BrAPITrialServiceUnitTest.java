package org.breedinginsight.brapi.v2.services;

import com.google.gson.JsonObject;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPISeason;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship;
import org.brapi.v2.model.pheno.BrAPIObservationUnitPosition;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationLevelDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapi.v2.dao.BrAPISeasonDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentExportQuery;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation.Columns;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.DatasetService;
import org.breedinginsight.model.BrAPIConstants;
import org.breedinginsight.model.Dataset;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.delta.DeltaEntityFactory;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.lock.DistributedLockService;
import org.breedinginsight.utilities.FileUtil;
import org.breedinginsight.utilities.Utilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.tablesaw.api.Table;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrAPITrialServiceUnitTest {
    private static final String REFERENCE_SOURCE = "breedinginsight.org";
    private static final String EXPORT_DATASET_ID = "33333333-3333-3333-3333-333333333333";
    private static final String DATASET_ID = "22222222-2222-2222-2222-222222222222";
    private static final String ENVIRONMENT_ID = "44444444-4444-4444-4444-444444444444";
    private static final String SECOND_ENVIRONMENT_ID = "55555555-5555-5555-5555-555555555555";

    private final BrAPITrialDAO trialDAO = mock(BrAPITrialDAO.class);
    private final BrAPIObservationDAO observationDAO = mock(BrAPIObservationDAO.class);
    private final BrAPIObservationUnitDAO observationUnitDAO = mock(BrAPIObservationUnitDAO.class);
    private final BrAPIListDAO listDAO = mock(BrAPIListDAO.class);
    private final TraitService traitService = mock(TraitService.class);
    private final BrAPIStudyDAO studyDAO = mock(BrAPIStudyDAO.class);
    private final BrAPISeasonDAO seasonDAO = mock(BrAPISeasonDAO.class);
    private final BrAPIObservationLevelDAO observationLevelDAO = mock(BrAPIObservationLevelDAO.class);
    private final BrAPIGermplasmDAO germplasmDAO = mock(BrAPIGermplasmDAO.class);
    private final FileMappingUtil fileMappingUtil = mock(FileMappingUtil.class);
    private final DistributedLockService lockService = mock(DistributedLockService.class);
    private final DatasetService datasetService = mock(DatasetService.class);
    private final DeltaEntityFactory deltaEntityFactory = mock(DeltaEntityFactory.class);
    private final BrAPIObservationLevelService observationLevelService = mock(BrAPIObservationLevelService.class);

    private BrAPITrialService service;
    private Program program;
    private BrAPITrial experiment;
    private BrAPIStudy study;
    private BrAPIGermplasm germplasm;
    private BrAPISeason season;

    @BeforeEach
    void setup() {
        service = new BrAPITrialService(
                REFERENCE_SOURCE,
                trialDAO,
                observationDAO,
                observationUnitDAO,
                listDAO,
                traitService,
                studyDAO,
                seasonDAO,
                observationUnitDAO,
                observationLevelDAO,
                germplasmDAO,
                fileMappingUtil,
                lockService,
                datasetService,
                deltaEntityFactory,
                observationLevelService
        );

        program = new Program();
        program.setId(UUID.randomUUID());
        program.setKey("TEST");

        experiment = new BrAPITrial();
        experiment.setTrialDbId("trial-db-id");
        experiment.setTrialName("Unit Test Experiment");
        experiment.setTrialDescription("Trial description");
        JsonObject experimentInfo = new JsonObject();
        experimentInfo.addProperty(BrAPIAdditionalInfoFields.EXPERIMENT_TYPE, "Phenotyping");
        experiment.setAdditionalInfo(experimentInfo);

        BrAPIExternalReference externalReference = new BrAPIExternalReference();
        externalReference.setReferenceSource(Utilities.generateReferenceSource(REFERENCE_SOURCE, ExternalReferenceSource.TRIALS));
        externalReference.setReferenceId("11111111-1111-1111-1111-111111111111");

        experiment.setExternalReferences(List.of(externalReference));

        study = new BrAPIStudy();
        study.setStudyDbId("study-1");
        study.setStudyName("Environment 1");
        study.setLocationName("Location 1");
        study.setSeasons(List.of("season-1"));
        study.setExternalReferences(List.of(createExternalReference(
                String.format("%s/%s", REFERENCE_SOURCE, ExternalReferenceSource.STUDIES.getName()),
                ENVIRONMENT_ID
        )));

        germplasm = new BrAPIGermplasm();
        germplasm.setGermplasmDbId("germ-1");
        germplasm.setAccessionNumber("G-1");

        season = new BrAPISeason();
        season.setSeasonDbId("season-1");
        season.setYear(2023);
    }

    @Test
    void exportObservationsFetchesSeasonOncePerDistinctSeasonAndWritesEnvYears() throws Exception {
        ExperimentExportQuery params = exportQuery(EXPORT_DATASET_ID);
        BrAPIStudy secondStudy = new BrAPIStudy();
        secondStudy.setStudyDbId("study-2");
        secondStudy.setStudyName("Environment 2");
        secondStudy.setLocationName("Location 2");
        secondStudy.setSeasons(List.of("season-2"));
        secondStudy.setExternalReferences(List.of(createExternalReference(
                String.format("%s/%s", REFERENCE_SOURCE, ExternalReferenceSource.STUDIES.getName()),
                SECOND_ENVIRONMENT_ID
        )));

        BrAPISeason secondSeason = new BrAPISeason();
        secondSeason.setSeasonDbId("season-2");
        secondSeason.setYear(2024);

        List<BrAPIObservationUnit> observationUnits = new ArrayList<>(List.of(
                createObservationUnit("ou-db-1", "plot-1"),
                createObservationUnit("ou-db-2", "plot-2", "study-2", "Environment 2", SECOND_ENVIRONMENT_ID)
        ));

        when(trialDAO.getTrialsByExperimentIds(eq(List.of(UUID.fromString("11111111-1111-1111-1111-111111111111"))), eq(program)))
                .thenReturn(List.of(experiment));
        when(studyDAO.getStudiesByExperimentID(eq(UUID.fromString("11111111-1111-1111-1111-111111111111")), eq(program)))
                .thenReturn(List.of(study, secondStudy));
        when(seasonDAO.getSeasonById("season-1", program.getId())).thenReturn(season);
        when(seasonDAO.getSeasonById("season-2", program.getId())).thenReturn(secondSeason);
        when(observationUnitDAO.getObservationUnitsForDataset(EXPORT_DATASET_ID, program)).thenReturn(observationUnits);
        when(listDAO.getListsByTypeAndExternalRef(any(), eq(program.getId()), any(), any())).thenReturn(Collections.<BrAPIListSummary>emptyList());
        when(observationDAO.getObservationsByObservationUnits(anyCollection(), eq(program))).thenReturn(Collections.<BrAPIObservation>emptyList());
        when(germplasmDAO.getGermplasmsByDBID(anyList(), eq(program.getId()))).thenReturn(List.of(germplasm));

        DownloadFile downloadFile = service.exportObservations(program, UUID.fromString("11111111-1111-1111-1111-111111111111"), params);

        Table exportTable = FileUtil.parseTableFromCsv(new ByteArrayInputStream(downloadFile.getStreamedFile().getInputStream().readAllBytes()));
        assertEquals(2, exportTable.rowCount());
        assertEquals(List.of("Environment 1", "Environment 2"), exportTable.stringColumn(Columns.ENV).asList());
        assertEquals(List.of(2023, 2024), exportTable.intColumn(Columns.ENV_YEAR).asList());
        verify(seasonDAO, times(1)).getSeasonById("season-1", program.getId());
        verify(seasonDAO, times(1)).getSeasonById("season-2", program.getId());
    }

    @Test
    void getDatasetDataFetchesSeasonOncePerDistinctSeasonAndWritesEnvYears() throws Exception {
        BrAPIStudy secondStudy = new BrAPIStudy();
        secondStudy.setStudyDbId("study-2");
        secondStudy.setStudyName("Environment 2");
        secondStudy.setLocationName("Location 2");
        secondStudy.setSeasons(List.of("season-2"));
        secondStudy.setExternalReferences(List.of(createExternalReference(
                String.format("%s/%s", REFERENCE_SOURCE, ExternalReferenceSource.STUDIES.getName()),
                SECOND_ENVIRONMENT_ID
        )));

        BrAPISeason secondSeason = new BrAPISeason();
        secondSeason.setSeasonDbId("season-2");
        secondSeason.setYear(2024);

        List<BrAPIObservationUnit> observationUnits = new ArrayList<>(List.of(
                createObservationUnit("ou-db-1", "plot-1"),
                createObservationUnit("ou-db-2", "plot-2", "study-2", "Environment 2", SECOND_ENVIRONMENT_ID)
        ));

        when(observationUnitDAO.getObservationUnitsForDataset(DATASET_ID, program)).thenReturn(observationUnits);
        when(studyDAO.getStudiesByStudyDbId(eq(Set.of("study-1", "study-2")), eq(program))).thenReturn(List.of(study, secondStudy));
        when(seasonDAO.getSeasonById("season-1", program.getId())).thenReturn(season);
        when(seasonDAO.getSeasonById("season-2", program.getId())).thenReturn(secondSeason);
        when(listDAO.getListsByTypeAndExternalRef(any(), eq(program.getId()), any(), any())).thenReturn(Collections.<BrAPIListSummary>emptyList());
        when(observationDAO.getObservationsByObservationUnitsAndVariables(anyList(), eq(Collections.emptyList()), eq(program)))
                .thenReturn(Collections.<BrAPIObservation>emptyList());

        Dataset dataset = service.getDatasetData(program, UUID.randomUUID(), UUID.fromString(DATASET_ID), false);

        assertEquals(2, dataset.observationUnits.size());
        assertEquals(List.of(2023, 2024), dataset.observationUnits.stream()
                .map(ou -> ou.getAdditionalInfo().get(BrAPIAdditionalInfoFields.ENV_YEAR).getAsInt())
                .collect(Collectors.toList()));
        verify(studyDAO, times(1)).getStudiesByStudyDbId(eq(Set.of("study-1", "study-2")), eq(program));
        verify(seasonDAO, times(1)).getSeasonById("season-1", program.getId());
        verify(seasonDAO, times(1)).getSeasonById("season-2", program.getId());
    }

    @Test
    void getDatasetDataThrowsWhenSeasonYearIsNull() throws Exception {
        // Dataset retrieval now shares the same year resolution path as export, so
        // unsupported null season years fail consistently across both entry points.
        List<BrAPIObservationUnit> observationUnits = List.of(createObservationUnit("ou-db-1", "plot-1"));
        season.setYear(null);

        when(observationUnitDAO.getObservationUnitsForDataset(DATASET_ID, program)).thenReturn(observationUnits);
        when(studyDAO.getStudiesByStudyDbId(eq(Set.of("study-1")), eq(program))).thenReturn(List.of(study));
        when(seasonDAO.getSeasonById("season-1", program.getId())).thenReturn(season);

        DoesNotExistException exception = assertThrows(DoesNotExistException.class,
                () -> service.getDatasetData(program, UUID.randomUUID(), UUID.fromString(DATASET_ID), false));

        assertEquals("Env Year not found for Study DbId = 'study-1'.", exception.getMessage());
    }

    private ExperimentExportQuery exportQuery(String datasetId) throws Exception {
        ExperimentExportQuery params = new ExperimentExportQuery();
        setField(params, "datasetId", datasetId);
        setField(params, "fileExtension", FileType.CSV);
        setField(params, "includeTimestamps", false);
        return params;
    }

    private BrAPIObservationUnit createObservationUnit(String observationUnitDbId, String observationUnitName) {
        return createObservationUnit(observationUnitDbId, observationUnitName, "study-1", "Environment 1", ENVIRONMENT_ID);
    }

    private BrAPIObservationUnit createObservationUnit(
            String observationUnitDbId,
            String observationUnitName,
            String studyDbId,
            String studyName,
            String environmentId) {
        BrAPIObservationUnit observationUnit = new BrAPIObservationUnit();
        observationUnit.setObservationUnitDbId(observationUnitDbId);
        observationUnit.setObservationUnitName(observationUnitName);
        observationUnit.setStudyDbId(studyDbId);
        observationUnit.setStudyName(studyName);
        observationUnit.setGermplasmDbId("germ-1");
        observationUnit.setGermplasmName("Germplasm 1");
        observationUnit.setExternalReferences(List.of(
                createExternalReference(
                        String.format("%s/%s", REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName()),
                        observationUnitDbId
                ),
                createExternalReference(
                        String.format("%s/%s", REFERENCE_SOURCE, ExternalReferenceSource.STUDIES.getName()),
                        environmentId
                )));
        observationUnit.setObservationUnitPosition(createObservationUnitPosition());
        return observationUnit;
    }

    private BrAPIObservationUnitPosition createObservationUnitPosition() {
        BrAPIObservationUnitPosition position = new BrAPIObservationUnitPosition();
        BrAPIObservationUnitLevelRelationship observationLevel = new BrAPIObservationUnitLevelRelationship();
        observationLevel.setLevelName("plot");
        observationLevel.setLevelCode("plot");
        observationLevel.setLevelOrder(1);
        position.setObservationLevel(observationLevel);

        BrAPIObservationUnitLevelRelationship repLevel = new BrAPIObservationUnitLevelRelationship();
        repLevel.setLevelName(BrAPIConstants.REPLICATE.getValue());
        repLevel.setLevelCode("1");
        repLevel.setLevelOrder(2);

        BrAPIObservationUnitLevelRelationship blockLevel = new BrAPIObservationUnitLevelRelationship();
        blockLevel.setLevelName(BrAPIConstants.BLOCK.getValue());
        blockLevel.setLevelCode("1");
        blockLevel.setLevelOrder(3);

        position.setObservationLevelRelationships(new ArrayList<>(List.of(repLevel, blockLevel)));
        return position;
    }

    private BrAPIExternalReference createExternalReference(String source, String id) {
        BrAPIExternalReference externalReference = new BrAPIExternalReference();
        externalReference.setReferenceSource(source);
        externalReference.setReferenceId(id);
        externalReference.setReferenceID(id);
        return externalReference;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
