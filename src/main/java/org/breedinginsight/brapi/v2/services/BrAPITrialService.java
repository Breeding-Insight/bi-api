package org.breedinginsight.brapi.v2.services;

import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.types.files.StreamedFile;
import org.brapi.client.v2.model.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentExportQuery;
import org.breedinginsight.brapps.importer.daos.*;
import org.breedinginsight.brapps.importer.model.base.AdditionalInfo;
import org.breedinginsight.brapps.importer.model.base.ObservationVariable;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.BrAPIConstants;
import org.breedinginsight.model.Column;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.experiment.ExperimentFileColumns;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.services.writers.ExcelWriter;
import org.breedinginsight.utilities.FileUtil;
import org.breedinginsight.utilities.Utilities;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrAPITrialService {

    private final BrAPITrialDAO trialDAO;
    private final BrAPIObservationUnitDAO ouDAO;
    private final BrAPIObservationDAO observationDAO;
    private final ProgramService programService;
    private final BrAPIListDAO listDAO;
    private final BrAPIObservationVariableDAO obsVarDAO;
    private final BrAPIStudyDAO studyDAO;
    private final BrAPIObservationUnitDAO ouDAO;
    private final BrAPIGermplasmDAO germplasmDAO;

    @Inject
    public BrAPITrialService(ProgramService programService,
                             BrAPITrialDAO trialDAO,
                             BrAPIObservationUnitDAO ouDAO,
                             BrAPIObservationDAO observationDAO,
                             BrAPIListDAO listDAO,
                             BrAPIObservationVariableDAO obsVarDAO,
                             BrAPIStudyDAO studyDAO,
                             BrAPIObservationUnitDAO ouDAO,
                             BrAPIGermplasmDAO germplasmDAO) {
        this.programService = programService;
        this.trialDAO = trialDAO;
        this.ouDAO = ouDAO;
        this.observationDAO = observationDAO;
        this.listDAO = listDAO;
        this.obsVarDAO = obsVarDAO;
        this.studyDAO = studyDAO;
        this.ouDAO = ouDAO;
        this.germplasmDAO = germplasmDAO;
    }

    public List<BrAPITrial> getExperiments(UUID programId) throws ApiException, DoesNotExistException {
        return trialDAO.getTrials(programId);
    }

    public BrAPITrial getTrialDataByUUID(UUID programId, UUID trialId, boolean stats) throws DoesNotExistException {
        try {
            BrAPITrial trial = trialDAO.getTrialById(programId,trialId).orElseThrow(() -> new DoesNotExistException("Trial does not exist"));
            //Remove the [program key] from the trial name
            trial.setTrialName( Utilities.removeUnknownProgramKey( trial.getTrialName()) );
            if( stats ){
                int environmentsCount = 1; // For now this is hardcoded to 1, because we are only supporting one environment per experiment
                long germplasmCount = countGermplasm(programId, trial.getTrialDbId());
                trial.putAdditionalInfoItem("environmentsCount", environmentsCount);
                trial.putAdditionalInfoItem("germplasmCount", germplasmCount);
            }
            return trial;
        } catch (ApiException e) {
            throw new InternalServerException(e.getMessage(), e);
        }
    }

    private long countGermplasm(UUID programId, String trialDbId) throws ApiException, DoesNotExistException{
        List<BrAPIObservationUnit> obUnits = ouDAO.getObservationUnitsForTrialDbId(programId, trialDbId);
        return obUnits.stream().map(BrAPIObservationUnit::getGermplasmDbId).distinct().count();
    public List<BrAPIObservationVariable> getDatasetObsVars(String datasetId, Program program) throws ApiException, DoesNotExistException {
        List<BrAPIListSummary> lists = listDAO.getListByTypeAndExternalRef(
                BrAPIListTypes.OBSERVATIONVARIABLES,
                program.getId(),
                String.format("%s/%s", referenceSource, ExternalReferenceSource.DATASET.getName()),
                UUID.fromString(datasetId));
        if (lists == null || lists.isEmpty()) {
            throw new DoesNotExistException("Dataset observation variables list not returned from BrAPI service");
        }
        String listDbId = lists.get(0).getListDbId();
        BrAPIListsSingleResponse list = listDAO.getListById(listDbId, program.getId());
        List<String> obsVarNames = list.getResult().getData();
        List<BrAPIObservationVariable> obsVars = obsVarDAO.getVariableByName(obsVarNames, program.getId());
        return obsVars;
    }

    public BrAPITrial getExperiment(Program program, UUID experimentId) throws ApiException {
        List<BrAPITrial> experiments = trialDAO.getTrialsByExperimentIds(List.of(experimentId), program);
        return experiments.get(0);
    }

    private void addObsVarDataToRow(
            Map<String, Object> row,
            BrAPIObservation obs,
            boolean includeTimestamp,
            List<BrAPIObservationVariable> obsVars,
            Program program) {
        // get observation variable for BrAPI observation
        BrAPIObservationVariable var = obsVars.stream()
                .filter(obsVar -> obs.getObservationVariableName().equals(obsVar.getObservationVariableName()))
                .collect(Collectors.toList()).get(0);

        String varName = Utilities.removeProgramKey(obs.getObservationVariableName(), program.getKey());
        if (var.getScale().getDataType().equals(BrAPITraitDataType.ORDINAL)) {
            row.put(varName, Integer.parseInt(obs.getValue()));
        } else if (var.getScale().getDataType().equals(BrAPITraitDataType.NUMERICAL) ||
                var.getScale().getDataType().equals(BrAPITraitDataType.DURATION)) {
            row.put(varName, Double.parseDouble(obs.getValue()));
        } else {
            row.put(varName, obs.getValue());
        }

        if (includeTimestamp) {
            row.put(String.format("TS:%s",varName), obs.getObservationTimeStamp());
        }
    }
    private List<Map<String, Object>> addBrAPIObsToRecords(
            List<Map<String, Object>> maps,
            List<BrAPIObservation> dataset,
            BrAPITrial experiment,
            Program program,
            boolean includeTimestamp,
            List<BrAPIObservationVariable> obsVars) throws ApiException, DoesNotExistException {
        // cache studies belonging to dataset
        Map<String, BrAPIStudy> studyByDbId = new HashMap<>();
        for (BrAPIObservation obs: dataset) {
            if (studyByDbId.get(obs.getStudyDbId()) == null) {
                BrAPIStudy study = studyDAO.getStudyByDbId(obs.getStudyDbId(), program)
                        .orElseThrow(() -> new DoesNotExistException("Study not returned from BrAPI Service"));
                studyByDbId.put(obs.getStudyDbId(), study);
            }
        }

        for (BrAPIObservation obs: dataset) {

            // get ouId
            List<BrAPIObservationUnit> ous = ouDAO.getObservationUnitByName(List.of(obs.getObservationUnitName()), program);
            if (ous.isEmpty()) {
                throw new DoesNotExistException("Observation unit not returned from BrAPI service");
            }
            BrAPIObservationUnit ou = ous.stream()
                    .filter(unit -> obs.getObservationUnitDbId().equals(unit.getObservationUnitDbId()))
                    .findAny()
                    .orElseThrow(() -> new RuntimeException());
            BrAPIExternalReference ouXref = Utilities.getExternalReference(
                            ou.getExternalReferences(),
                            String.format("%s/%s", referenceSource, ExternalReferenceSource.OBSERVATION_UNITS.getName()))
                    .orElseThrow(() -> new RuntimeException("observation unit id not found"));
            String ouId = ouXref.getReferenceID();

            // if there is a row with that ouId then just add the obs var data and timestamp to the row
            Optional<Map<String, Object>> existingRow = maps.stream()
                    .filter(row -> ouId.equals(row.get(ExperimentObservation.Columns.OBS_UNIT_ID)))
                    .findAny();
            if (existingRow.isPresent()) {
                addObsVarDataToRow(existingRow.get(), obs, includeTimestamp, obsVars, program);
            } else {

                // otherwise, make a new row
                HashMap<String, Object> row = new HashMap<>();
                BrAPIGermplasm germplasm = germplasmDAO.getGermplasmByDBID(obs.getGermplasmDbId(), program.getId())
                        .orElseThrow(() -> new DoesNotExistException("Germplasm not returned from BrAPI service"));
                BrAPIStudy study = studyByDbId.get(obs.getStudyDbId());
                row.put(ExperimentObservation.Columns.GERMPLASM_NAME, obs.getGermplasmName());
                row.put(ExperimentObservation.Columns.GERMPLASM_GID, germplasm.getAccessionNumber());
                row.put(ExperimentObservation.Columns.TEST_CHECK, ou.getObservationUnitPosition().getEntryType().toString());
                row.put(ExperimentObservation.Columns.EXP_TITLE, experiment.getTrialName());
                row.put(ExperimentObservation.Columns.EXP_DESCRIPTION, experiment.getTrialDescription());
                row.put(ExperimentObservation.Columns.EXP_UNIT, ou.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.OBSERVATION_LEVEL).getAsString());
                row.put(ExperimentObservation.Columns.EXP_TYPE, experiment.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.EXPERIMENT_TYPE).getAsString());
                row.put(ExperimentObservation.Columns.ENV, obs.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.STUDY_NAME).getAsString());
                row.put(ExperimentObservation.Columns.ENV_LOCATION, study.getLocationName());
                row.put(ExperimentObservation.Columns.ENV_YEAR, obs.getSeason().getYear());
                row.put(ExperimentObservation.Columns.EXP_UNIT_ID, obs.getObservationUnitName());

                // get replicate number
                Optional<BrAPIObservationUnitLevelRelationship> repLevel = ou.getObservationUnitPosition()
                        .getObservationLevelRelationships().stream()
                        .filter(level -> BrAPIConstants.REPLICATE.getValue().equals(level.getLevelName()))
                        .findFirst();
                if (repLevel.isPresent()) {
                    row.put(ExperimentObservation.Columns.REP_NUM, Integer.parseInt(repLevel.get().getLevelCode()));
                }

                //get block number
                Optional<BrAPIObservationUnitLevelRelationship> blockLevel = ou.getObservationUnitPosition()
                        .getObservationLevelRelationships().stream()
                        .filter(level -> BrAPIConstants.BLOCK.getValue().equals(level.getLevelName()))
                        .findFirst();
                if (blockLevel.isPresent()) {
                    row.put(ExperimentObservation.Columns.BLOCK_NUM, Integer.parseInt(blockLevel.get().getLevelCode()));
                }

                if (ou.getObservationUnitPosition() != null) {
                    row.put(ExperimentObservation.Columns.ROW, Double.parseDouble(ou.getObservationUnitPosition().getPositionCoordinateX()));
                    row.put(ExperimentObservation.Columns.COLUMN, Double.parseDouble(ou.getObservationUnitPosition().getPositionCoordinateY()));
                }

                if (ou.getTreatments() != null && !ou.getTreatments().isEmpty()) {
                    row.put(ExperimentObservation.Columns.TREATMENT_FACTORS, ou.getTreatments().get(0).getFactor().toString());
                } else {
                    row.put(ExperimentObservation.Columns.TREATMENT_FACTORS, null);
                }

                row.put(ExperimentObservation.Columns.OBS_UNIT_ID, ouId);
                addObsVarDataToRow(row, obs, includeTimestamp, obsVars, program);
                maps.add(row);
            }
        }
        return maps;
    }

    private List<Column> addObsVarColumns(
            List<Column> columns,
            List<BrAPIObservationVariable> obsVars,
            boolean includeTimestamps,
            Program program) {
        for (BrAPIObservationVariable var: obsVars) {
            Column obsVarColumn = new Column();
            obsVarColumn.setDataType(Column.ColumnDataType.STRING);
            if (var.getScale().getDataType().equals(BrAPITraitDataType.ORDINAL)) {
                obsVarColumn.setDataType(Column.ColumnDataType.INTEGER);
            }
            if (var.getScale().getDataType().equals(BrAPITraitDataType.NUMERICAL) ||
                    var.getScale().getDataType().equals(BrAPITraitDataType.DURATION)) {
                obsVarColumn.setDataType(Column.ColumnDataType.DOUBLE);
            }
            String varName = Utilities.removeProgramKey(var.getObservationVariableName(), program.getKey());
            obsVarColumn.setValue(varName);
            columns.add(obsVarColumn);
            if (includeTimestamps) {
                columns.add(new Column(String.format("TS:%s",varName),Column.ColumnDataType.STRING));
            }
        }
        return columns;
    }
    private String makeFileName(BrAPITrial experiment, Program program, String envName) {
        // <exp-title>_Observation Dataset [<prog-key>-<exp-seq>]_<environment>_<export-timestamp>
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
        String timestamp = formatter.format(OffsetDateTime.now());
        return String.format("%s_Observation Dataset [%s-%s]_%s_%s",
                experiment.getTrialName(),
                program.getKey(),
                experiment.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER).getAsString(),
                envName,
                timestamp);
    }
    private List<BrAPIObservation> filterDatasetByEnvironment(List<BrAPIObservation> dataset, List<String> envNames) {
            return dataset.stream().filter(obs -> envNames.contains(
                    obs.getAdditionalInfo().getAsJsonObject()
                            .get(BrAPIAdditionalInfoFields.STUDY_NAME).getAsString())).collect(Collectors.toList());
    }
    public DownloadFile exportObservations(
            Program program,
            UUID experimentId,
            ExperimentExportQuery params) throws IOException, DoesNotExistException, ApiException, ParsingException {
        // process params


        FileType fileType = params.getFileExtension();

        // get BrAPI observations for requested environments
        List<BrAPIObservation> dataset = getObservationDataset(program, experimentId);
        if (params.getEnvironments() != null) {
            List<String> envNames = new ArrayList<>(Arrays.asList(params.getEnvironments().split(",")));
            dataset = filterDatasetByEnvironment(dataset, envNames);
        }

        // add columns in the export for any observation variables
        BrAPITrial experiment = getExperiment(program, experimentId);
        List<BrAPIObservationVariable> obsVars = new ArrayList<>();
        List<Column> columns = ExperimentFileColumns.getOrderedColumns();
        if (experiment.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID) != null) {
            String obsDatasetId = experiment
                    .getAdditionalInfo().getAsJsonObject()
                    .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString();
            obsVars = getDatasetObsVars(obsDatasetId, program);
            columns = addObsVarColumns(columns, obsVars, params.isIncludeTimestamps(), program);
        }

        StreamedFile downloadFile;
        List<Map<String, Object>> experimentObservationRecords = new ArrayList<>();


        experimentObservationRecords = addBrAPIObsToRecords(experimentObservationRecords, dataset, experiment, program, params.isIncludeTimestamps(), obsVars);

        if (fileType.equals(FileType.CSV)){
            downloadFile = CSVWriter.writeToDownload(columns, experimentObservationRecords, fileType);
        } else {
            downloadFile = ExcelWriter.writeToDownload("Dataset Export", columns, experimentObservationRecords, fileType);
        }

        String envFilenameFragment = params.getEnvironments() == null ? "All Environments" : params.getEnvironments();
        String fileName = makeFileName(experiment, program, envFilenameFragment) + fileType.getExtension();
        return new DownloadFile(fileName, downloadFile);
    }

    private Map<String, Object> makeExpObsMapByHeader(ExperimentObservation obs) {
        Map<String, Object> row = new HashMap<>();
        if (obs != null) {
            row.put(ExperimentObservation.Columns.GERMPLASM_NAME, obs.getGermplasmName());
            row.put(ExperimentObservation.Columns.GERMPLASM_GID, obs.getGid());
            row.put(ExperimentObservation.Columns.TEST_CHECK, obs.getTestOrCheck());
            row.put(ExperimentObservation.Columns.EXP_TITLE, obs.getExpTitle());
            row.put(ExperimentObservation.Columns.EXP_UNIT, obs.getExpUnit());
            row.put(ExperimentObservation.Columns.EXP_TYPE, obs.getExpType());
            row.put(ExperimentObservation.Columns.ENV, obs.getEnv());
            row.put(ExperimentObservation.Columns.ENV_LOCATION, obs.getEnvLocation());
            row.put(ExperimentObservation.Columns.ENV_YEAR, obs.getEnvYear());
            row.put(ExperimentObservation.Columns.EXP_UNIT_ID, obs.getExpUnitId());
            row.put(ExperimentObservation.Columns.REP_NUM, obs.getExpReplicateNo());
            row.put(ExperimentObservation.Columns.BLOCK_NUM, obs.getExpBlockNo());
            row.put(ExperimentObservation.Columns.ROW, obs.getRow());
            row.put(ExperimentObservation.Columns.COLUMN, obs.getColumn());
        }

        return row;
    }
    public List<BrAPIObservation> getObservationDataset(Program program, UUID experimentId) throws DoesNotExistException {
        List<BrAPIObservation> dataset = new ArrayList<>();
        try {
            List<BrAPITrial> trials = trialDAO.getTrialsByExperimentIds(List.of(experimentId), program);
            if (trials.isEmpty()) {
                throw new DoesNotExistException("Experiment not found");
            }
            BrAPITrial datasetTrial = trials.stream().filter(trial -> {
                return trial
                        .getAdditionalInfo()
                        .getAsJsonObject()
                        .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID) != null;
            }).findFirst().orElseThrow(() -> new DoesNotExistException("Experiment dataset not found"));
            dataset = observationDAO.getObservationsByTrialDbId(List.of(datasetTrial.getTrialDbId()), program);
        } catch (ApiException e) {
            log.error("Error fetching BrAPI observations: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        } catch (DoesNotExistException e) {
            log.error("Trial does not exist", e);
            throw new DoesNotExistException(e.getMessage());
        }
        return dataset;
    }
    public List<BrAPIObservation> getDataset(Program program, UUID experimentId, UUID datasetId) throws ApiException, DoesNotExistException {
        List<BrAPIObservation> dataset = new ArrayList<>();
        try {
            List<BrAPITrial> trials = trialDAO.getTrialsByExperimentIds(List.of(experimentId), program);
            if (trials.isEmpty()) {
                throw new DoesNotExistException("Experiment not found");
            }
            BrAPITrial datasetTrial = trials.stream().filter(trial -> {
                String id = trial
                        .getAdditionalInfo()
                        .getAsJsonObject(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString();
                return id != null;
            }).findAny().orElseThrow(() -> new DoesNotExistException("Experiment dataset not found"));
            dataset = observationDAO.getObservationsByTrialDbId(List.of(datasetTrial.getTrialDbId()), program);
        } catch (ApiException e) {
            log.error("Error fetching BrAPI observations: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        } catch (DoesNotExistException e) {
            log.error("Trial does not exist", e);
            throw new DoesNotExistException(e.getMessage());
        }
        return dataset;
    }
}
