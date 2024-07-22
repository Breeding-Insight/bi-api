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

package org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow.steps;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.JSON;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.ChangeLogEntry;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.model.workflow.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessedPhenotypeData;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.ExperimentSeasonService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.Trait;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Singleton
@Slf4j
public class ValidatePendingImportObjectsStep {

    private static final String BLANK_FIELD_EXPERIMENT = "Field is blank when creating a new experiment";
    private static final String ENV_LOCATION_MISMATCH = "All locations must be the same for a given environment";
    private static final String BLANK_FIELD_ENV = "Field is blank when creating a new environment";
    private static final String BLANK_FIELD_OBS = "Field is blank when creating new observations";
    private static final String ENV_YEAR_MISMATCH = "All years must be the same for a given environment";

    private final ExperimentSeasonService experimentSeasonService;
    private final Gson gson;

    @Inject
    public ValidatePendingImportObjectsStep(ExperimentSeasonService experimentSeasonService) {
        this.experimentSeasonService = experimentSeasonService;
        this.gson = new JSON().getGson();
    }

    public ValidationErrors process(ImportContext importContext, PendingData pendingData, ProcessedPhenotypeData phenotypeData, ProcessedData processedData) {

        //Map<Integer, PendingImport> mappedBrAPIImport = processedData.getMappedBrAPIImport();
        List<BrAPIImport> importRows = importContext.getImportRows();
        List<Column<?>> phenotypeCols = phenotypeData.getPhenotypeCols();
        Program program = importContext.getProgram();
        List<Trait> referencedTraits = phenotypeData.getReferencedTraits();
        boolean commit = importContext.isCommit();
        User user = importContext.getUser();

        Map<Integer, PendingImport> mappedBrAPIImport = prepareDataForValidation(importRows, pendingData, phenotypeCols);
        ValidationErrors validationErrors = validateFields(importRows, mappedBrAPIImport, referencedTraits, program, phenotypeCols, commit, user, pendingData, phenotypeData);
        processedData.setMappedBrAPIImport(mappedBrAPIImport);
        return validationErrors;
    }

    private Map<Integer, PendingImport> prepareDataForValidation(List<BrAPIImport> importRows,
                                          PendingData pendingData,
                                          List<Column<?>> phenotypeCols) {

        Map<Integer, PendingImport> mappedBrAPIImport = new HashMap<>();

        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = pendingData.getObservationUnitByNameNoScope();
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = pendingData.getTrialByNameNoScope();
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = pendingData.getStudyByNameNoScope();
        Map<String, PendingImportObject<ProgramLocation>> locationByName = pendingData.getLocationByName();
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = pendingData.getObsVarDatasetByName();
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = pendingData.getExistingGermplasmByGID();
        Map<String, PendingImportObject<BrAPIObservation>> observationByHash = pendingData.getObservationByHash();

        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(rowNum, new PendingImport());
            List<PendingImportObject<BrAPIObservation>> observations = mappedImportRow.getObservations();
            String observationHash;

            // NOTE: Removed append/update workflow code
            mappedImportRow.setTrial(trialByNameNoScope.get(importRow.getExpTitle()));
            mappedImportRow.setLocation(locationByName.get(importRow.getEnvLocation()));
            mappedImportRow.setStudy(studyByNameNoScope.get(importRow.getEnv()));
            mappedImportRow.setObservationUnit(observationUnitByNameNoScope.get(ExperimentUtilities.createObservationUnitKey(importRow)));
            mappedImportRow.setGermplasm(getGidPIO(pendingData, importRow));

            // loop over phenotype column observation data for current row
            for (Column<?> column : phenotypeCols) {

                // if value was blank won't be entry in map for this observation
                observations.add(observationByHash.get(ExperimentUtilities.getImportObservationHash(importRow, ExperimentUtilities.getVariableNameFromColumn(column))));
            }

            mappedBrAPIImport.put(rowNum, mappedImportRow);
        }

        return mappedBrAPIImport;
    }

    private PendingImportObject<BrAPIGermplasm> getGidPIO(PendingData pendingData, ExperimentObservation importRow) {

        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = pendingData.getExistingGermplasmByGID();

        if (existingGermplasmByGID.containsKey(importRow.getGid())) {
            return existingGermplasmByGID.get(importRow.getGid());
        }

        return null;
    }

    private ValidationErrors validateFields(List<BrAPIImport> importRows, Map<Integer, PendingImport> mappedBrAPIImport, List<Trait> referencedTraits, Program program,
                                List<Column<?>> phenotypeCols, boolean commit, User user,
                                PendingData pendingData,
                                ProcessedPhenotypeData phenotypeData) {
        //fetching any existing observations for any OUs in the import
        CaseInsensitiveMap<String, Trait> colVarMap = new CaseInsensitiveMap<>();
        ValidationErrors validationErrors = new ValidationErrors();

        for ( Trait trait: referencedTraits) {
            colVarMap.put(trait.getObservationVariableName(),trait);
        }
        Set<String> uniqueStudyAndObsUnit = new HashSet<>();
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
            PendingImport mappedImportRow = mappedBrAPIImport.get(rowNum);
            // NOTE: validate Observations used by both workflows
            if (StringUtils.isNotBlank(importRow.getGid())) { // if GID is blank, don't bother to check if it is valid.
                validateGermplasm(importRow, validationErrors, rowNum, mappedImportRow.getGermplasm());
            }
            validateTestOrCheck(importRow, validationErrors, rowNum);
            validateConditionallyRequired(pendingData, validationErrors, rowNum, importRow, program, commit);
            validateObservationUnits(pendingData, validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);
            validateObservations(pendingData, phenotypeData, validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
        }

        return validationErrors;
    }

    private void validateGermplasm(ExperimentObservation importRow, ValidationErrors validationErrors, int rowNum, PendingImportObject<BrAPIGermplasm> germplasmPIO) {
        // error if GID is not blank but GID does not already exist
        if (StringUtils.isNotBlank(importRow.getGid()) && germplasmPIO == null) {
            ExperimentUtilities.addRowError(ExperimentObservation.Columns.GERMPLASM_GID, "A non-existing GID", validationErrors, rowNum);
        }
    }

    private void validateTestOrCheck(ExperimentObservation importRow, ValidationErrors validationErrors, int rowNum) {
        String testOrCheck = importRow.getTestOrCheck();
        if ( ! ( testOrCheck==null || testOrCheck.isBlank()
                || "C".equalsIgnoreCase(testOrCheck) || "CHECK".equalsIgnoreCase(testOrCheck)
                || "T".equalsIgnoreCase(testOrCheck) || "TEST".equalsIgnoreCase(testOrCheck) )
        ){
            ExperimentUtilities.addRowError(ExperimentObservation.Columns.TEST_CHECK, String.format("Invalid value (%s)", testOrCheck), validationErrors, rowNum) ;
        }
    }

    private void validateConditionallyRequired(PendingData pendingData, ValidationErrors validationErrors, int rowNum, ExperimentObservation importRow, Program program, boolean commit) {
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = pendingData.getTrialByNameNoScope();
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = pendingData.getStudyByNameNoScope();

        ImportObjectState expState = trialByNameNoScope.get(importRow.getExpTitle())
                .getState();
        ImportObjectState envState = studyByNameNoScope.get(importRow.getEnv()).getState();

        String errorMessage = BLANK_FIELD_EXPERIMENT;
        if (expState == ImportObjectState.EXISTING && envState == ImportObjectState.NEW) {
            errorMessage = BLANK_FIELD_ENV;
        } else if(expState == ImportObjectState.EXISTING && envState == ImportObjectState.EXISTING) {
            errorMessage = BLANK_FIELD_OBS;
        }

        if(expState == ImportObjectState.NEW || envState == ImportObjectState.NEW) {
            validateRequiredCell(importRow.getGid(), ExperimentObservation.Columns.GERMPLASM_GID, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpTitle(), ExperimentObservation.Columns.EXP_TITLE,errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpUnit(), ExperimentObservation.Columns.EXP_UNIT, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpType(), ExperimentObservation.Columns.EXP_TYPE, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getEnv(), ExperimentObservation.Columns.ENV, errorMessage, validationErrors, rowNum);
            if(validateRequiredCell(importRow.getEnvLocation(), ExperimentObservation.Columns.ENV_LOCATION, errorMessage, validationErrors, rowNum)) {
                if(!Utilities.removeProgramKeyAndUnknownAdditionalData(studyByNameNoScope.get(importRow.getEnv()).getBrAPIObject().getLocationName(), program.getKey()).equals(importRow.getEnvLocation())) {
                    ExperimentUtilities.addRowError(ExperimentObservation.Columns.ENV_LOCATION, ENV_LOCATION_MISMATCH, validationErrors, rowNum);
                }
            }
            if(validateRequiredCell(importRow.getEnvYear(), ExperimentObservation.Columns.ENV_YEAR, errorMessage, validationErrors, rowNum)) {
                String studyYear = StringUtils.defaultString(studyByNameNoScope.get(importRow.getEnv()).getBrAPIObject().getSeasons().get(0) );
                String rowYear = importRow.getEnvYear();
                if(commit) {
                    rowYear = experimentSeasonService.yearToSeasonDbId(importRow.getEnvYear(), program.getId());
                }
                if(StringUtils.isNotBlank(studyYear) && !studyYear.equals(rowYear)) {
                    ExperimentUtilities.addRowError(ExperimentObservation.Columns.ENV_YEAR, ENV_YEAR_MISMATCH, validationErrors, rowNum);
                }
            }
            validateRequiredCell(importRow.getExpUnitId(), ExperimentObservation.Columns.EXP_UNIT_ID, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpReplicateNo(), ExperimentObservation.Columns.REP_NUM, errorMessage, validationErrors, rowNum);
            validateRequiredCell(importRow.getExpBlockNo(), ExperimentObservation.Columns.BLOCK_NUM, errorMessage, validationErrors, rowNum);

            if(StringUtils.isNotBlank(importRow.getObsUnitID())) {
                ExperimentUtilities.addRowError(ExperimentObservation.Columns.OBS_UNIT_ID, "ObsUnitID cannot be specified when creating a new environment", validationErrors, rowNum);
            }
        } else {
            //Check if existing environment. If so, ObsUnitId must be assigned
            validateRequiredCell(
                    importRow.getObsUnitID(),
                    ExperimentObservation.Columns.OBS_UNIT_ID,
                    ExperimentUtilities.MISSING_OBS_UNIT_ID_ERROR,
                    validationErrors,
                    rowNum
            );
        }
    }

    private boolean validateRequiredCell(String value, String columnHeader, String errorMessage, ValidationErrors validationErrors, int rowNum) {
        if (StringUtils.isBlank(value)) {
            ExperimentUtilities.addRowError(columnHeader, errorMessage, validationErrors, rowNum);
            return false;
        }
        return true;
    }

    private void validateObservationUnits(
            PendingData pendingData,
            ValidationErrors validationErrors,
            Set<String> uniqueStudyAndObsUnit,
            int rowNum,
            ExperimentObservation importRow) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = pendingData.getObservationUnitByNameNoScope();

        validateUniqueObsUnits(validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);

        String key = ExperimentUtilities.createObservationUnitKey(importRow);
        PendingImportObject<BrAPIObservationUnit> ouPIO = observationUnitByNameNoScope.get(key);
        if(ouPIO.getState() == ImportObjectState.NEW && StringUtils.isNotBlank(importRow.getObsUnitID())) {
            ExperimentUtilities.addRowError(ExperimentObservation.Columns.OBS_UNIT_ID, "Could not find observation unit by ObsUnitDBID", validationErrors, rowNum);
        }

        validateGeoCoordinates(validationErrors, rowNum, importRow);
    }

    /**
     * Validate that the observation unit is unique within a study.
     * <br>
     * SIDE EFFECTS:  validationErrors and uniqueStudyAndObsUnit can be modified.
     *
     * @param validationErrors      can be modified as a side effect.
     * @param uniqueStudyAndObsUnit can be modified as a side effect.
     * @param rowNum                     counter that is always two less the file row being validated
     * @param importRow             the data row being validated
     */
    private void validateUniqueObsUnits(
            ValidationErrors validationErrors,
            Set<String> uniqueStudyAndObsUnit,
            int rowNum,
            ExperimentObservation importRow) {
        String envIdPlusStudyId = ExperimentUtilities.createObservationUnitKey(importRow);
        if (uniqueStudyAndObsUnit.contains(envIdPlusStudyId)) {
            String errorMessage = String.format("The ID (%s) is not unique within the environment(%s)", importRow.getExpUnitId(), importRow.getEnv());
            ExperimentUtilities.addRowError(ExperimentObservation.Columns.EXP_UNIT_ID, errorMessage, validationErrors, rowNum);
        } else {
            uniqueStudyAndObsUnit.add(envIdPlusStudyId);
        }
    }

    private void validateGeoCoordinates(ValidationErrors validationErrors, int rowNum, ExperimentObservation importRow) {

        String lat = importRow.getLatitude();
        String lon = importRow.getLongitude();
        String elevation = importRow.getElevation();

        // If any of Lat, Long, or Elevation are provided, Lat and Long must both be provided.
        if (StringUtils.isNotBlank(lat) || StringUtils.isNotBlank(lon) || StringUtils.isNotBlank(elevation)) {
            if (StringUtils.isBlank(lat)) {
                ExperimentUtilities.addRowError(ExperimentObservation.Columns.LAT, "Latitude must be provided for complete coordinate specification", validationErrors, rowNum);
            }
            if (StringUtils.isBlank(lon)) {
                ExperimentUtilities.addRowError(ExperimentObservation.Columns.LONG, "Longitude must be provided for complete coordinate specification", validationErrors, rowNum);
            }
        }

        // Validate coordinate values
        boolean latBadValue = false;
        boolean lonBadValue = false;
        boolean elevationBadValue = false;
        double latDouble;
        double lonDouble;
        double elevationDouble;

        // Only check latitude format if not blank since already had previous error
        if (StringUtils.isNotBlank(lat)) {
            try {
                latDouble = Double.parseDouble(lat);
                if (latDouble < -90 || latDouble > 90) {
                    latBadValue = true;
                }
            } catch (NumberFormatException e) {
                latBadValue = true;
            }
        }

        // Only check longitude format if not blank since already had previous error
        if (StringUtils.isNotBlank(lon)) {
            try {
                lonDouble = Double.parseDouble(lon);
                if (lonDouble < -180 || lonDouble > 180) {
                    lonBadValue = true;
                }
            } catch (NumberFormatException e) {
                lonBadValue = true;
            }
        }

        if (StringUtils.isNotBlank(elevation)) {
            try {
                elevationDouble = Double.parseDouble(elevation);
            } catch (NumberFormatException e) {
                elevationBadValue = true;
            }
        }

        if (latBadValue) {
            ExperimentUtilities.addRowError(ExperimentObservation.Columns.LAT, "Invalid Lat value (expected range -90 to 90)", validationErrors, rowNum);
        }

        if (lonBadValue) {
            ExperimentUtilities.addRowError(ExperimentObservation.Columns.LONG, "Invalid Long value (expected range -180 to 180)", validationErrors, rowNum);
        }

        if (elevationBadValue) {
            ExperimentUtilities.addRowError(ExperimentObservation.Columns.LONG, "Invalid Elevation value (numerals expected)", validationErrors, rowNum);
        }

    }


    private void validateObservations(PendingData pendingData,
                                      ProcessedPhenotypeData phenotypeData,
                                      ValidationErrors validationErrors,
                                      int rowNum,
                                      ExperimentObservation importRow,
                                      List<Column<?>> phenotypeCols,
                                      CaseInsensitiveMap<String, Trait> colVarMap,
                                      boolean commit,
                                      User user) {

        Map<String, BrAPIObservation> existingObsByObsHash = pendingData.getExistingObsByObsHash();
        Map<String, Column<?>> timeStampColByPheno = phenotypeData.getTimeStampColByPheno();
        Map<String, PendingImportObject<BrAPIObservation>> observationByHash = pendingData.getObservationByHash();

        phenotypeCols.forEach(phenoCol -> {
            String importHash;
            String importObsValue = phenoCol.getString(rowNum);

            // NOTE: removed append / update specifc code
            importHash = ExperimentUtilities.getImportObservationHash(importRow, phenoCol.name());

            // error if import observation data already exists and user has not selected to overwrite
            if(commit && "false".equals(importRow.getOverwrite() == null ? "false" : importRow.getOverwrite()) &&
                    existingObsByObsHash.containsKey(importHash) &&
                    StringUtils.isNotBlank(phenoCol.getString(rowNum)) &&
                    !existingObsByObsHash.get(importHash).getValue().equals(phenoCol.getString(rowNum))) {
                ExperimentUtilities.addRowError(
                        phenoCol.name(),
                        String.format("Value already exists for ObsUnitId: %s, Phenotype: %s", importRow.getObsUnitID(), phenoCol.name()),
                        validationErrors, rowNum
                );

                // preview case where observation has already been committed and the import row ObsVar data differs from what
                // had been saved prior to import
            } else if (existingObsByObsHash.containsKey(importHash) && !ExperimentUtilities.isObservationMatched(phenotypeData, pendingData, importHash, importObsValue, phenoCol, rowNum)) {

                // different data means validations still need to happen
                // TODO consider moving these two calls into a separate method since called twice together
                ExperimentUtilities.validateObservationValue(colVarMap.get(phenoCol.name()), phenoCol.getString(rowNum), phenoCol.name(), validationErrors, rowNum);

                //Timestamp validation
                if(timeStampColByPheno.containsKey(phenoCol.name())) {
                    Column<?> timeStampCol = timeStampColByPheno.get(phenoCol.name());
                    ExperimentUtilities.validateTimeStampValue(timeStampCol.getString(rowNum), timeStampCol.name(), validationErrors, rowNum);
                }

                // add a change log entry when updating the value of an observation
                // only will update and thereby need change log entry if no error
                if (commit && (!validationErrors.hasErrors())) {
                    BrAPIObservation pendingObservation = observationByHash.get(importHash).getBrAPIObject();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
                    String timestamp = formatter.format(OffsetDateTime.now());
                    String reason = importRow.getOverwriteReason() != null ? importRow.getOverwriteReason() : "";
                    String prior = "";
                    if (ExperimentUtilities.isValueMatched(pendingData, importHash, importObsValue)) {
                        prior.concat(existingObsByObsHash.get(importHash).getValue());
                    }
                    if (timeStampColByPheno.containsKey(phenoCol.name()) && ExperimentUtilities.isTimestampMatched(pendingData, importHash, timeStampColByPheno.get(phenoCol.name()).getString(rowNum))) {
                        prior = prior.isEmpty() ? prior : prior.concat(" ");
                        prior.concat(existingObsByObsHash.get(importHash).getObservationTimeStamp().toString());
                    }
                    ChangeLogEntry change = new ChangeLogEntry(prior,
                            reason,
                            user.getId(),
                            timestamp
                    );

                    // create the changelog field in additional info if it does not already exist
                    if (pendingObservation.getAdditionalInfo().isJsonNull()) {
                        pendingObservation.setAdditionalInfo(new JsonObject());
                        pendingObservation.getAdditionalInfo().add(BrAPIAdditionalInfoFields.CHANGELOG, new JsonArray());
                    }

                    if (pendingObservation.getAdditionalInfo() != null && !pendingObservation.getAdditionalInfo().has(BrAPIAdditionalInfoFields.CHANGELOG)) {
                        pendingObservation.getAdditionalInfo().add(BrAPIAdditionalInfoFields.CHANGELOG, new JsonArray());
                    }

                    // add a new entry to the changelog
                    pendingObservation.getAdditionalInfo().get(BrAPIAdditionalInfoFields.CHANGELOG).getAsJsonArray().add(gson.toJsonTree(change).getAsJsonObject());
                }

                // preview case where observation has already been committed and import ObsVar data is the
                // same as has been committed prior to import
            } else if(ExperimentUtilities.isObservationMatched(phenotypeData, pendingData, importHash, importObsValue, phenoCol, rowNum)) {
                BrAPIObservation existingObs = existingObsByObsHash.get(importHash);
                existingObs.setObservationVariableName(phenoCol.name());
                observationByHash.get(importHash).setState(ImportObjectState.EXISTING);
                observationByHash.get(importHash).setBrAPIObject(existingObs);

                // preview case where observation has already been committed and import ObsVar data is empty prior to import
            } else if(!existingObsByObsHash.containsKey(importHash) && (StringUtils.isBlank(phenoCol.getString(rowNum)))) {
                observationByHash.get(importHash).setState(ImportObjectState.EXISTING);
            } else {
                ExperimentUtilities.validateObservationValue(colVarMap.get(phenoCol.name()), phenoCol.getString(rowNum), phenoCol.name(), validationErrors, rowNum);

                //Timestamp validation
                if(timeStampColByPheno.containsKey(phenoCol.name())) {
                    Column<?> timeStampCol = timeStampColByPheno.get(phenoCol.name());
                    ExperimentUtilities.validateTimeStampValue(timeStampCol.getString(rowNum), timeStampCol.name(), validationErrors, rowNum);
                }
            }
        });
    }
}
