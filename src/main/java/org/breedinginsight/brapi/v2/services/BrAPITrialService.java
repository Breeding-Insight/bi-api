package org.breedinginsight.brapi.v2.services;

import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.types.files.StreamedFile;
import org.brapi.client.v2.model.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.brapi.v2.model.pheno.BrAPITraitDataType;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentExportQuery;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationVariableDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.base.AdditionalInfo;
import org.breedinginsight.brapps.importer.model.base.ObservationVariable;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Column;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.parsers.experiment.ExperimentFileColumns;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.services.writers.ExcelWriter;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
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

    @Inject
    public BrAPITrialService(ProgramService programService,
                             BrAPITrialDAO trialDAO,
                             BrAPIObservationUnitDAO ouDAO,
                             BrAPIObservationDAO observationDAO,
                             BrAPIListDAO listDAO,
                             BrAPIObservationVariableDAO obsVarDAO) {
        this.programService = programService;
        this.trialDAO = trialDAO;
        this.ouDAO = ouDAO;
        this.observationDAO = observationDAO;
        this.listDAO = listDAO;
        this.obsVarDAO = obsVarDAO;
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

    private List<Map<String, Object>> addBrAPIObsToRecords(List<Map<String, Object>> maps, List<BrAPIObservation> dataset) {
        for (BrAPIObservation obs: dataset) {
            HashMap<String, Object> row = new HashMap<>();
            row.put("Germplasm Name", obs.getGermplasmName());
            row.put("Exp Unit ID", obs.getObservationUnitName());
            row.put("Env", obs.getAdditionalInfo().getAsJsonObject().get("studyName").getAsString());

            maps.add(row);
        }
        return maps;
    }

    private List<Column> addObsVarColumns(List<Column> columns, List<BrAPIObservationVariable> obsVars) {
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
            obsVarColumn.setValue(var.getObservationVariableName());
            columns.add(obsVarColumn);
        }
        return columns;
    }
    private String makeFileName() {
        // <exp-title>_Observation Dataset [<prog-key>-<exp-seq>]_<environment>_<export-timestamp>
        return "Exp_Observation Dataset [KEY-EXP]_ENV";
    }
    private List<BrAPIObservation> filterDatasetByEnvironment(List<BrAPIObservation> dataset, List<String> envNames) {
            return dataset.stream().filter(obs -> envNames.contains(
                    obs.getAdditionalInfo().getAsJsonObject()
                            .get("studyName").getAsString())).collect(Collectors.toList());
    }
    public DownloadFile exportObservations(
            Program program,
            UUID experimentId,
            ExperimentExportQuery params
    ) throws IOException, DoesNotExistException, ApiException {
        List<BrAPIObservation> dataset = getObservationDataset(program, experimentId);
        if (params.getEnvironments() != null) {
            List<String> envNames = new ArrayList<>(Arrays.asList(params.getEnvironments().split(",")));
            dataset = filterDatasetByEnvironment(dataset, envNames);
        }
        BrAPITrial experiment = getExperiment(program, experimentId);
        String obsDatasetId = experiment
                .getAdditionalInfo().getAsJsonObject()
                .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID.toString()).getAsString();
        List<Column> columns = ExperimentFileColumns.getOrderedColumns();
        if (obsDatasetId != null) {
            List<BrAPIObservationVariable> obsVars = getDatasetObsVars(obsDatasetId, program);
            columns = addObsVarColumns(columns, obsVars);
        }



        FileType fileExtension = FileType.XLSX;
        String fileName = makeFileName();
        StreamedFile downloadFile;
        List<Map<String, Object>> experimentObservationRecords = new ArrayList<>();

        experimentObservationRecords = addBrAPIObsToRecords(experimentObservationRecords, dataset);

        if (fileExtension.equals(FileType.CSV)){
            downloadFile = CSVWriter.writeToDownload(columns, experimentObservationRecords, fileExtension);
        } else {
            downloadFile = ExcelWriter.writeToDownload("Dataset Export", columns, experimentObservationRecords, fileExtension);
        }

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
                String id = trial
                        .getAdditionalInfo()
                        .getAsJsonObject()
                        .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)
                        .getAsString();
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
