package org.breedinginsight.brapi.v2.services;

import com.github.filosganga.geogson.model.Coordinates;
import com.github.filosganga.geogson.model.positions.SinglePosition;
import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.types.files.StreamedFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.*;
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;

import org.brapi.v2.model.pheno.*;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.*;
import org.breedinginsight.brapi.v2.model.request.query.ExperimentExportQuery;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation.Columns;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.model.BrAPIConstants;
import org.breedinginsight.model.Column;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.*;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.parsers.experiment.ExperimentFileColumns;
import org.breedinginsight.utilities.IntOrderComparator;
import org.breedinginsight.utilities.FileUtil;
import org.breedinginsight.utilities.Utilities;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Singleton
public class BrAPITrialService {
    private final String referenceSource;
    private final BrAPITrialDAO trialDAO;
    private final BrAPIObservationDAO observationDAO;
    private final BrAPIListDAO listDAO;

    private final TraitService traitService;
    private final BrAPIStudyDAO studyDAO;
    private final BrAPISeasonDAO seasonDAO;
    private final BrAPIObservationUnitDAO ouDAO;
    private final BrAPIGermplasmDAO germplasmDAO;
    private final FileMappingUtil fileMappingUtil;
    private static final String SHEET_NAME = "Data";

    @Inject
    public BrAPITrialService(@Property(name = "brapi.server.reference-source") String referenceSource,
                             BrAPITrialDAO trialDAO,
                             BrAPIObservationDAO observationDAO,
                             BrAPIListDAO listDAO,
                             TraitService traitService,
                             BrAPIStudyDAO studyDAO,
                             BrAPISeasonDAO seasonDAO,
                             BrAPIObservationUnitDAO ouDAO,
                             BrAPIGermplasmDAO germplasmDAO,
                             FileMappingUtil fileMappingUtil) {

        this.referenceSource = referenceSource;
        this.trialDAO = trialDAO;
        this.observationDAO = observationDAO;
        this.listDAO = listDAO;
        this.traitService = traitService;
        this.studyDAO = studyDAO;
        this.seasonDAO = seasonDAO;
        this.ouDAO = ouDAO;
        this.germplasmDAO = germplasmDAO;
        this.fileMappingUtil = fileMappingUtil;
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
                log.debug("fetching experiment: " + trialId + " stats");
                int environmentsCount = 1; // For now this is hardcoded to 1, because we are only supporting one environment per experiment
                log.debug("fetching observation units for experiment: " + trialId);
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
    }

    public DownloadFile exportObservations(
            Program program,
            UUID experimentId,
            ExperimentExportQuery params) throws IOException, DoesNotExistException, ApiException {
        String logHash = UUID.randomUUID().toString();
        log.debug(logHash + ": exporting experiment: "+experimentId+", params: " + params);
        DownloadFile downloadFile;
        boolean isDataset = false;
        List<BrAPIObservation> dataset = new ArrayList<>();
        List<Trait> obsVars = new ArrayList<>();
        Map<String, Map<String, Object>> rowByOUId = new HashMap<>();
        Map<String, BrAPIStudy> studyByDbId = new HashMap<>();
        Map<String, String> studyDbIdByOUId = new HashMap<>();
        List<String> requestedEnvIds = StringUtils.isNotBlank(params.getEnvironments()) ?
                new ArrayList<>(Arrays.asList(params.getEnvironments().split(","))) : new ArrayList<>();
        FileType fileType = params.getFileExtension();

        // make columns present in all exports
        List<Column> columns = ExperimentFileColumns.getOrderedColumns();

        // add columns for requested dataset obsvars and timestamps
        log.debug(logHash + ": fetching experiment for export");
        BrAPITrial experiment = getExperiment(program, experimentId);

        // get requested environments for the experiment
        log.debug(logHash + ": fetching environments for export");
        List<BrAPIStudy> expStudies = studyDAO.getStudiesByExperimentID(experimentId, program);
        if (!requestedEnvIds.isEmpty()) {
            expStudies = expStudies
                    .stream()
                    .filter(study -> requestedEnvIds.contains(getStudyId(study)))
                    .collect(Collectors.toList());
        }
        expStudies.forEach(study -> studyByDbId.putIfAbsent(study.getStudyDbId(), study));

        // get the OUs for the requested environments
        log.debug(logHash + ": fetching OUs for export");
        List<BrAPIObservationUnit> ous = new ArrayList<>();
        Map<String, BrAPIObservationUnit> ouByOUDbId = new HashMap<>();
        try {
            for (BrAPIStudy study: expStudies) {
                List<BrAPIObservationUnit> studyOUs = ouDAO.getObservationUnitsForStudyDbId(study.getStudyDbId(), program);
                studyOUs.forEach(ou -> ouByOUDbId.put(ou.getObservationUnitDbId(), ou));
                ous.addAll(studyOUs);
            }
        } catch (ApiException err) {
            log.error(logHash + ": Error fetching observation units for a study by its DbId" +
                    Utilities.generateApiExceptionLogMessage(err), err);
        }

        if ((StringUtils.isBlank(params.getDataset()) || "observations".equalsIgnoreCase(params.getDataset())) &&
                experiment.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID) != null) {
            String obsDatasetId = experiment
                    .getAdditionalInfo().getAsJsonObject()
                    .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString();
            isDataset = true;
            log.debug(logHash + ": fetching dataset observation variables for export");
            obsVars = getDatasetObsVars(obsDatasetId, program);

            // make additional columns in the export for each obs variable and obs variable timestamp
            addObsVarColumns(columns, obsVars, params.isIncludeTimestamps(), program);

        }

        // make export rows from any observations
        if (isDataset) {
            log.debug(logHash + ": fetching observations for export");
            dataset = observationDAO.getObservationsByTrialDbId(List.of(experiment.getTrialDbId()), program);
        }
        if (!requestedEnvIds.isEmpty()) {
            log.debug(logHash + ": filtering observations to only requested environments for export");
            dataset = filterDatasetByEnvironment(dataset, requestedEnvIds, studyByDbId);
        }

        log.debug(logHash + ": fetching program's germplasm for export");
        List<BrAPIGermplasm> programGermplasm = germplasmDAO.getGermplasmsByDBID(ouByOUDbId.values().stream().map(BrAPIObservationUnit::getGermplasmDbId).collect(Collectors.toList()), program.getId());
        Map<String, BrAPIGermplasm> programGermplasmByDbId = programGermplasm.stream().collect(Collectors.toMap(BrAPIGermplasm::getGermplasmDbId, Function.identity()));

        log.debug(logHash + ": populating rows for export");
        // Update rowByOUId and studyDbIdByOUId.
        addBrAPIObsToRecords(
                dataset,
                experiment,
                program,
                ouByOUDbId,
                studyByDbId,
                rowByOUId,
                params.isIncludeTimestamps(),
                obsVars,
                studyDbIdByOUId,
                programGermplasmByDbId
        );


        // make export rows for OUs without observations
        if (rowByOUId.size() < ous.size()) {
            for (BrAPIObservationUnit ou: ous) {
                String ouId = getOUId(ou);
                // Map Observation Unit to the Study it belongs to.
                studyDbIdByOUId.put(ouId, ou.getStudyDbId());
                if (!rowByOUId.containsKey(ouId)) {
                    rowByOUId.put(ouId, createExportRow(experiment, program, ou, studyByDbId, programGermplasmByDbId));
                }
            }
        }

        log.debug(logHash + ": writing data to file for export");
        // If one or more envs requested, create a separate file for each env, then zip if there are multiple.
        if (!requestedEnvIds.isEmpty()) {
            // This will hold a list of rows for each study, each list will become a separate file.
            Map<String, List<Map<String, Object>>> rowsByStudyId = new HashMap<>();

            for (Map<String, Object> row: rowByOUId.values()) {
                String studyId = studyDbIdByOUId.get((String)row.get(ExperimentObservation.Columns.OBS_UNIT_ID));
                // Initialize key with empty list if it is not present.
                if (!rowsByStudyId.containsKey(studyId))
                {
                    rowsByStudyId.put(studyId, new ArrayList<>());
                }
                // Add row to appropriate list in rowsByStudyId.
                rowsByStudyId.get(studyId).add(row);
            }
            List<DownloadFile> files = new ArrayList<>();
            // Generate a file for each study.
            for (Map.Entry<String, List<Map<String, Object>>> entry: rowsByStudyId.entrySet()) {
                List<Map<String, Object>> rows = entry.getValue();
                sortDefaultForExportRows(rows);
                StreamedFile streamedFile = FileUtil.writeToStreamedFile(columns, rows, fileType, SHEET_NAME);
                String name = makeFileName(experiment, program, studyByDbId.get(entry.getKey()).getStudyName()) + fileType.getExtension();
                // Add to file list.
                files.add(new DownloadFile(name, streamedFile));
            }
            if (files.size() == 1) {
                // Don't zip, as there is a single file.
                downloadFile = files.get(0);
            }
            else {
                log.debug(logHash + ": zipping files for export");
                // Zip, as there are multiple files.
                StreamedFile zipFile = zipFiles(files);
                downloadFile = new DownloadFile(makeZipFileName(experiment, program), zipFile);
            }
        } else {
            List<Map<String, Object>> exportRows = new ArrayList<>(rowByOUId.values());
            sortDefaultForExportRows(exportRows);
            // write export data to requested file format
            StreamedFile streamedFile = FileUtil.writeToStreamedFile(columns, exportRows, fileType, SHEET_NAME);
            // Set filename.
            String envFilenameFragment = params.getEnvironments() == null ? "All Environments" : params.getEnvironments();
            String fileName = makeFileName(experiment, program, envFilenameFragment) + fileType.getExtension();
            downloadFile = new DownloadFile(fileName, streamedFile);
        }

        return downloadFile;
    }

    private StreamedFile zipFiles(List<DownloadFile> files) throws IOException {
        PipedInputStream in = new PipedInputStream();
        final PipedOutputStream out = new PipedOutputStream(in);
        new Thread(() -> {
            try {
                ZipOutputStream zipStream = new ZipOutputStream(out);
                // Add each file to zip.
                for (DownloadFile datasetFile : files) {

                    ZipEntry entry = new ZipEntry(datasetFile.getFileName());
                    zipStream.putNextEntry(entry);
                    // Write datasetFile to zip.
                    zipStream.write(datasetFile.getStreamedFile().getInputStream().readAllBytes());
                    zipStream.closeEntry();

                }
                zipStream.close();
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
        // NOTE: Micronaut doesn't define application/zip in MediaType, use application/octet-stream.
        return new StreamedFile(in, new MediaType(MediaType.APPLICATION_OCTET_STREAM));
    }

    public Dataset getDatasetData(Program program, UUID experimentId, UUID datsetId, Boolean stats) throws ApiException, DoesNotExistException {
        log.debug("fetching dataset: " + datsetId + " for experiment: " + experimentId + ".  including stats: " + stats);
        log.debug("fetching observationUnits for dataset: " + datsetId);
        List<BrAPIObservationUnit> datasetOUs = ouDAO.getObservationUnitsForDataset(datsetId.toString(), program);
        log.debug("fetching dataset variables dataset: " + datsetId);
        List<Trait> datasetObsVars = getDatasetObsVars(datsetId.toString(), program);
        List<String> ouDbIds = datasetOUs.stream().map(BrAPIObservationUnit::getObservationUnitDbId).collect(Collectors.toList());
        List<String> obsVarDbIds = datasetObsVars.stream().map(Trait::getObservationVariableDbId).collect(Collectors.toList());
        log.debug("fetching observations for dataset: " + datsetId);
        List<BrAPIObservation> data = observationDAO.getObservationsByObservationUnitsAndVariables(ouDbIds, obsVarDbIds, program);
        log.debug("building dataset object for dataset: " + datsetId);
        sortDefaultForObservationUnit(datasetOUs);
        Dataset dataset = new Dataset(experimentId.toString(), data, datasetOUs, datasetObsVars);
        if (stats) {
            Integer ouCount = datasetOUs.size();
            Integer obsVarCount = datasetObsVars.size();
            Integer obsCount = ouCount * obsVarCount;
            Integer obsDataCount = data.size();
            Integer emptyDataCount = obsCount - obsDataCount;
            dataset = dataset.setStat(Dataset.DatasetStat.OBSERVATION_UNITS, ouCount)
                    .setStat(Dataset.DatasetStat.PHENOTYPES, obsVarCount)
                    .setStat(Dataset.DatasetStat.OBSERVATIONS, obsCount)
                    .setStat(Dataset.DatasetStat.OBSERVATIONS_WITH_DATA, obsDataCount)
                    .setStat(Dataset.DatasetStat.OBSERVATIONS_WITHOUT_DATA, emptyDataCount);
        }

        return dataset;
    }

    private void addBrAPIObsToRecords(
            List<BrAPIObservation> dataset,
            BrAPITrial experiment,
            Program program,
            Map<String, BrAPIObservationUnit> ouByOUDbId,
            Map<String, BrAPIStudy> studyByDbId,
            Map<String, Map<String, Object>> rowByOUId,
            boolean includeTimestamp,
            List<Trait> obsVars,
            Map<String, String> studyDbIdByOUId,
            Map<String, BrAPIGermplasm> programGermplasmByDbId) throws ApiException, DoesNotExistException {
        Map<String, Trait> varByDbId = new HashMap<>();
        obsVars.forEach(var -> varByDbId.put(var.getObservationVariableDbId(), var));
        for (BrAPIObservation obs: dataset) {

            // get observation unit for observation
            BrAPIObservationUnit ou = ouByOUDbId.get(obs.getObservationUnitDbId());
            String ouId = getOUId(ou);

            // get observation variable for BrAPI observation
            Trait var = varByDbId.get(obs.getObservationVariableDbId());

            // if there is a row with that ouId then just add the obs var data and timestamp to the row
            if (rowByOUId.get(ouId) != null) {
                addObsVarDataToRow(rowByOUId.get(ouId), obs, includeTimestamp, var, program);
            } else {

                // otherwise make a new row
                Map<String, Object> row = createExportRow(experiment, program, ou, studyByDbId, programGermplasmByDbId);
                addObsVarDataToRow(row, obs, includeTimestamp, var, program);
                rowByOUId.put(ouId, row);
            }

            // Map Observation Unit to the Study it belongs to.
            studyDbIdByOUId.put(ouId, ou.getStudyDbId());
        }
    }

    private String getOUId(BrAPIObservationUnit ou) {
        BrAPIExternalReference ouXref = Utilities.getExternalReference(
                        ou.getExternalReferences(),
                        String.format("%s/%s", referenceSource, ExternalReferenceSource.OBSERVATION_UNITS.getName()))
                .orElseThrow(() -> new RuntimeException("observation unit id not found"));
        return ouXref.getReferenceID();
    }

    private String getStudyId(BrAPIStudy study) {
        // HACK: avoid null reference exceptions.
        if (study == null) return null;
        BrAPIExternalReference studyXref = Utilities.getExternalReference(
                        study.getExternalReferences(),
                        String.format("%s/%s", referenceSource, ExternalReferenceSource.STUDIES.getName()))
                .orElseThrow(() -> new RuntimeException("study id not found"));
        return studyXref.getReferenceID();
    }

    private void addObsVarDataToRow(
            Map<String, Object> row,
            BrAPIObservation obs,
            boolean includeTimestamp,
            Trait var,
            Program program) {
        String varName = Utilities.removeProgramKey(obs.getObservationVariableName(), program.getKey());
        if (!(obs.getValue().equalsIgnoreCase("NA")) && (var.getScale().getDataType().equals(DataType.NUMERICAL) ||
                var.getScale().getDataType().equals(DataType.DURATION))) {
            row.put(varName, Double.parseDouble(obs.getValue()));
        } else {
            row.put(varName, obs.getValue());
        }

        if (includeTimestamp) {
            String stamp = obs.getObservationTimeStamp() == null ? "" : obs.getObservationTimeStamp().toString();
            row.put(String.format("TS:%s",varName), stamp);
        }
    }

    public List<Trait> getDatasetObsVars(String datasetId, Program program) throws ApiException, DoesNotExistException {
        List<BrAPIListSummary> lists = listDAO.getListByTypeAndExternalRef(
                BrAPIListTypes.OBSERVATIONVARIABLES,
                program.getId(),
                String.format("%s/%s", referenceSource, ExternalReferenceSource.DATASET.getName()),
                UUID.fromString(datasetId));
        if (lists == null || lists.isEmpty()) {
            log.warn(String.format("Dataset %s observation variables list not returned from BrAPI service", datasetId));
            return new ArrayList<>();
        }
        String listDbId = lists.get(0).getListDbId();
        BrAPIListsSingleResponse list = listDAO.getListById(listDbId, program.getId());
        List<String> obsVarNames = list.getResult().getData().stream().map(var -> Utilities.removeProgramKey(var, program.getKey())).collect(Collectors.toList());
        log.debug("Searching for dataset obsVars: " + obsVarNames);
        List<Trait> obsVars = traitService.getByName(program.getId(), obsVarNames);
        log.debug(String.format("Found %d obsVars", obsVars.size()));

        // sort the obsVars to match the order stored in the dataset list
        return fileMappingUtil.sortByField(obsVarNames, obsVars, Trait::getObservationVariableName);
    }

    public BrAPITrial getExperiment(Program program, UUID experimentId) throws ApiException {
        List<BrAPITrial> experiments = trialDAO.getTrialsByExperimentIds(List.of(experimentId), program);
        if (experiments.isEmpty()) {
            throw new RuntimeException("A trial with given experiment id was not returned");
        }

        return experiments.get(0);
    }

    private Map<String, Object> createExportRow(
            BrAPITrial experiment,
            Program program,
            BrAPIObservationUnit ou,
            Map<String, BrAPIStudy> studyByDbId,
            Map<String, BrAPIGermplasm> programGermplasmByDbId) throws ApiException, DoesNotExistException {
        HashMap<String, Object> row = new HashMap<>();

        // get OU id, germplasm, and study
        BrAPIExternalReference ouXref = Utilities.getExternalReference(
                        ou.getExternalReferences(),
                        String.format("%s/%s", referenceSource, ExternalReferenceSource.OBSERVATION_UNITS.getName()))
                .orElseThrow(() -> new RuntimeException("observation unit id not found"));
        String ouId = ouXref.getReferenceID();
        BrAPIGermplasm germplasm = Optional.ofNullable(programGermplasmByDbId.get(ou.getGermplasmDbId()))
                .orElseThrow(() -> new DoesNotExistException("Germplasm not returned from BrAPI service"));
        BrAPIStudy study = studyByDbId.get(ou.getStudyDbId());

        // make export row from BrAPI objects
        row.put(ExperimentObservation.Columns.GERMPLASM_NAME, Utilities.removeProgramKey(ou.getGermplasmName(), program.getKey(), germplasm.getAccessionNumber()));
        row.put(ExperimentObservation.Columns.GERMPLASM_GID, germplasm.getAccessionNumber());

        // use only the capitalized first character of the entry type for test/check
        BrAPIEntryTypeEnum entryType = ou.getObservationUnitPosition().getEntryType();
        String testCheck = entryType != null ? String.valueOf(Character.toUpperCase(entryType.toString().charAt(0))) : null;
        row.put(ExperimentObservation.Columns.TEST_CHECK, testCheck);
        row.put(ExperimentObservation.Columns.EXP_TITLE, Utilities.removeProgramKey(experiment.getTrialName(), program.getKey()));
        row.put(ExperimentObservation.Columns.EXP_DESCRIPTION, experiment.getTrialDescription());
        row.put(ExperimentObservation.Columns.EXP_UNIT, ou.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.OBSERVATION_LEVEL).getAsString());
        row.put(ExperimentObservation.Columns.EXP_TYPE, experiment.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.EXPERIMENT_TYPE).getAsString());
        row.put(ExperimentObservation.Columns.ENV, Utilities.removeProgramKeyAndUnknownAdditionalData(study.getStudyName(), program.getKey()));
        row.put(ExperimentObservation.Columns.ENV_LOCATION, Utilities.removeProgramKey(study.getLocationName(), program.getKey()));

        // Lat, Long, Elevation
        Coordinates coordinates = extractCoordinates(ou);
        Optional.ofNullable(coordinates)
                .map(c -> doubleToString(c.getLat()))
                .ifPresent(lat -> row.put(ExperimentObservation.Columns.LAT, lat));
        Optional.ofNullable(coordinates)
                .map(c -> doubleToString(c.getLon()))
                .ifPresent(lon -> row.put(ExperimentObservation.Columns.LONG, lon));
        Optional.ofNullable(coordinates)
                .map(c -> doubleToString(c.getAlt()))
                .ifPresent(elevation -> row.put(ExperimentObservation.Columns.ELEVATION, elevation));

        // RTK
        JsonObject additionalInfo = ou.getAdditionalInfo();
        String rtk = ( additionalInfo==null || additionalInfo.get(BrAPIAdditionalInfoFields.RTK) ==null )
                        ?   null
                        :   additionalInfo.get(BrAPIAdditionalInfoFields.RTK).getAsString();
        row.put(ExperimentObservation.Columns.RTK, rtk);

        BrAPISeason season = seasonDAO.getSeasonById(study.getSeasons().get(0), program.getId());
        row.put(ExperimentObservation.Columns.ENV_YEAR, season.getYear());
        row.put(ExperimentObservation.Columns.EXP_UNIT_ID, Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()));

        // get replicate number
        Optional<BrAPIObservationUnitLevelRelationship> repLevel = ou.getObservationUnitPosition()
                .getObservationLevelRelationships().stream()
                .filter(level -> BrAPIConstants.REPLICATE.getValue().equals(level.getLevelName()))
                .findFirst();
        repLevel.ifPresent(brAPIObservationUnitLevelRelationship ->
                row.put(ExperimentObservation.Columns.REP_NUM, Integer.parseInt(brAPIObservationUnitLevelRelationship.getLevelCode())));

        //get block number
        Optional<BrAPIObservationUnitLevelRelationship> blockLevel = ou.getObservationUnitPosition()
                .getObservationLevelRelationships().stream()
                .filter(level -> BrAPIConstants.BLOCK.getValue().equals(level.getLevelName()))
                .findFirst();
        blockLevel.ifPresent(brAPIObservationUnitLevelRelationship ->
                row.put(ExperimentObservation.Columns.BLOCK_NUM, Integer.parseInt(brAPIObservationUnitLevelRelationship.getLevelCode())));

        //Row and Column
        if ( ou.getObservationUnitPosition() != null ) {
            row.put(ExperimentObservation.Columns.ROW, ou.getObservationUnitPosition().getPositionCoordinateX());
            row.put(ExperimentObservation.Columns.COLUMN, ou.getObservationUnitPosition().getPositionCoordinateY());
        }

        if (ou.getTreatments() != null && !ou.getTreatments().isEmpty()) {
            row.put(ExperimentObservation.Columns.TREATMENT_FACTORS, ou.getTreatments().get(0).getFactor());
        } else {
            row.put(ExperimentObservation.Columns.TREATMENT_FACTORS, null);
        }
        row.put(ExperimentObservation.Columns.OBS_UNIT_ID, ouId);

        return row;
    }

    private String doubleToString(double val){
        return Double.isNaN(val) ? null : String.valueOf( val );
    }
    private Coordinates extractCoordinates(BrAPIObservationUnit ou){
        Coordinates coordinates = null;
        if (        ou.getObservationUnitPosition()!=null
                &&  ou.getObservationUnitPosition().getGeoCoordinates()!=null
                &&  ou.getObservationUnitPosition().getGeoCoordinates().getGeometry()!=null
                &&  ou.getObservationUnitPosition().getGeoCoordinates().getGeometry().positions()!=null
        )
        {
            Object o = ou.getObservationUnitPosition().getGeoCoordinates().getGeometry().positions();
            if (o instanceof SinglePosition){
                SinglePosition sp = (SinglePosition)o;
                coordinates= sp.coordinates();
            }
        }
        return coordinates;
    }

    private void addObsVarColumns(
            List<Column> columns,
            List<Trait> obsVars,
            boolean includeTimestamps,
            Program program) {
        for (Trait var: obsVars) {
            Column obsVarColumn = new Column();
            obsVarColumn.setDataType(Column.ColumnDataType.STRING);
            if (var.getScale().getDataType().equals(DataType.NUMERICAL) ||
                    var.getScale().getDataType().equals(DataType.DURATION)) {
                obsVarColumn.setDataType(Column.ColumnDataType.DOUBLE);
            }
            String varName = Utilities.removeProgramKey(var.getObservationVariableName(), program.getKey());
            obsVarColumn.setValue(varName);
            columns.add(obsVarColumn);
            if (includeTimestamps) {
                columns.add(new Column(String.format("TS:%s",varName),Column.ColumnDataType.STRING));
            }
        }
    }
    private String makeFileName(BrAPITrial experiment, Program program, String envName) {
        // <exp-title>_Observation Dataset_<environment>_<export-timestamp>
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_hh-mm-ssZ");
        String timestamp = formatter.format(OffsetDateTime.now());
        String unsafeName = String.format("%s_Observation Dataset_%s_%s",
                Utilities.removeProgramKey(experiment.getTrialName(), program.getKey()),
                Utilities.removeProgramKeyAndUnknownAdditionalData(envName, program.getKey()),
                timestamp);
        // Make file name safe for all platforms.
        return Utilities.makePortableFilename(unsafeName);
    }

    private String makeZipFileName(BrAPITrial experiment, Program program) {
        // <exp-title_<export-timestamp>.zip
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_hh-mm-ssZ");
        String timestamp = formatter.format(OffsetDateTime.now());
        String unsafeName = String.format("%s_%s.zip",
                Utilities.removeProgramKey(experiment.getTrialName(), program.getKey()),
                timestamp);
        // Make file name safe for all platforms.
        return Utilities.makePortableFilename(unsafeName);
    }

    private List<BrAPIObservation> filterDatasetByEnvironment(
            List<BrAPIObservation> dataset,
            List<String> envIds,
            Map<String, BrAPIStudy> studyByDbId) {
        return dataset
                .stream()
                .filter(obs -> envIds.contains(getStudyId(studyByDbId.get(obs.getStudyDbId()))))
                .collect(Collectors.toList());
    }

    private void sortDefaultForObservationUnit(List<BrAPIObservationUnit> ous) {
        Comparator<BrAPIObservationUnit> studyNameComparator = Comparator.comparing(BrAPIObservationUnit::getStudyName, new IntOrderComparator());
        Comparator<BrAPIObservationUnit> ouNameComparator = Comparator.comparing(BrAPIObservationUnit::getObservationUnitName, new IntOrderComparator());
        ous.sort( (studyNameComparator).thenComparing(ouNameComparator));
    }

    private void sortDefaultForExportRows(@NotNull List<Map<String, Object>> exportRows) {
        Comparator<Map<String, Object>> envComparator = Comparator.comparing(row -> (row.get(Columns.ENV).toString()), new IntOrderComparator());
        Comparator<Map<String, Object>> expUnitIdComparator =
                Comparator.comparing(row -> (row.get(Columns.EXP_UNIT_ID).toString()), new IntOrderComparator());

        exportRows.sort(envComparator.thenComparing(expUnitIdComparator));
     }

    public BrAPIStudy getEnvironment(Program program, UUID envId) throws ApiException {
        List<BrAPIStudy> environments = studyDAO.getStudiesByEnvironmentIds(List.of(envId), program);
        if (environments.isEmpty()) {
            throw new RuntimeException("A study with given experiment id was not returned");
        }

        return environments.get(0);
    }
}
