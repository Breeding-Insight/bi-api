package org.breedinginsight.brapi.v2.services;

import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.types.files.StreamedFile;
import org.brapi.client.v2.model.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.base.AdditionalInfo;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.model.Column;
import org.breedinginsight.model.DownloadFile;
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

    @Inject
    public BrAPITrialService(ProgramService programService, BrAPITrialDAO trialDAO, BrAPIObservationUnitDAO ouDAO, BrAPIObservationDAO observationDAO) {
        this.programService = programService;
        this.trialDAO = trialDAO;
        this.ouDAO = ouDAO;
        this.observationDAO = observationDAO;

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
    private String makeFileName() {
        // <exp-title>_Observation Dataset [<prog-key>-<exp-seq>]_<environment>_<export-timestamp>
        return "Exp_Observation Dataset [KEY-EXP]_ENV";
    }
    public DownloadFile exportObservations(UUID programId, UUID experimentId, Object queryParams ) throws IOException {
        List<Column> columns = ExperimentFileColumns.getOrderedColumns();
        FileType fileExtension = FileType.XLSX;
        String fileName = makeFileName();
        StreamedFile downloadFile;
        List<Map<String, Object>> processedData = new ArrayList<>();

        if (fileExtension.equals(FileType.CSV)){
            downloadFile = CSVWriter.writeToDownload(columns, processedData, fileExtension);
        } else {
            downloadFile = ExcelWriter.writeToDownload("Dataset Export", columns, processedData, fileExtension);
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
    public List<BrAPIObservation> getDataset(UUID programId, UUID experimentId, UUID datasetId) throws ApiException, DoesNotExistException {
        List<BrAPIObservation> dataset = new ArrayList<>();
        try {
            List<BrAPITrial> trials = trialDAO.getTrialsByExperimentIds(List.of(experimentId), programId);
            if (trials.isEmpty()) {
                throw new DoesNotExistException("Experiment not found");
            }
            BrAPITrial datasetTrial = trials.stream().filter(trial -> {
                String id = trial
                        .getAdditionalInfo()
                        .getAsJsonObject(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString();
                return id != null;
            }).findAny().orElseThrow(() -> new DoesNotExistException("Experiment dataset not found"));
            dataset = observationDAO.getObservationsByTrialDbId(List.of(datasetTrial.getTrialDbId()), programId);
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
