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
package org.breedinginsight.brapps.importer.services.processors.experiment;

import com.google.gson.JsonObject;
import io.micronaut.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessedPhenotypeData;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import tech.tablesaw.columns.Column;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ExperimentUtilities {

    public static final CharSequence COMMA_DELIMITER = ",";
    public static final String TIMESTAMP_PREFIX = "TS:";
    public static final String TIMESTAMP_REGEX = "^"+TIMESTAMP_PREFIX+"\\s*";
    public static final String MIDNIGHT = "T00:00:00-00:00";
    public static final String MULTIPLE_EXP_TITLES = "File contains more than one Experiment Title";
    public static final String PREEXISTING_EXPERIMENT_TITLE = "Experiment Title already exists";
    public static final String MISSING_OBS_UNIT_ID_ERROR = "Experimental entities are missing ObsUnitIDs";



    public static List<ExperimentObservation> importRowsToExperimentObservations(List<BrAPIImport> importRows) {
        return importRows.stream()
                .map(trialImport -> (ExperimentObservation) trialImport)
                .collect(Collectors.toList());
    }

    public static boolean validDateTimeValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    public static String getObservationHash(String observationUnitName, String variableName, String studyName) {
        String concat = DigestUtils.sha256Hex(observationUnitName) +
                DigestUtils.sha256Hex(variableName) +
                DigestUtils.sha256Hex(StringUtils.defaultString(studyName));
        return DigestUtils.sha256Hex(concat);
    }

    /*
     * this finds the YEAR from the season list on the BrAPIStudy and then
     * will add the year to the additionalInfo-field of the BrAPIStudy
     * */
    public static void addYearToStudyAdditionalInfo(Program program, BrAPIStudy study) {
        JsonObject additionalInfo = study.getAdditionalInfo();

        //if it is already there, don't add it.
        if(additionalInfo==null || additionalInfo.get(BrAPIAdditionalInfoFields.ENV_YEAR)==null) {
            String year = study.getSeasons().get(0);
            addYearToStudyAdditionalInfo(program, study, year);
        }
    }

    /*
     * this will add the given year to the additionalInfo field of the BrAPIStudy (if it does not already exist)
     * */
    public static void addYearToStudyAdditionalInfo(Program program, BrAPIStudy study, String year) {
        JsonObject additionalInfo = study.getAdditionalInfo();
        if (additionalInfo==null){
            additionalInfo = new JsonObject();
            study.setAdditionalInfo(additionalInfo);
        }
        if( additionalInfo.get(BrAPIAdditionalInfoFields.ENV_YEAR)==null) {
            additionalInfo.addProperty(BrAPIAdditionalInfoFields.ENV_YEAR, year);
        }
    }

    public static String createObservationUnitKey(ExperimentObservation importRow) {
        return createObservationUnitKey(importRow.getEnv(), importRow.getExpUnitId());
    }

    public static String createObservationUnitKey(String studyName, String obsUnitName) {
        return studyName + obsUnitName;
    }

    public static String getImportObservationHash(ExperimentObservation importRow, String variableName) {
        return getObservationHash(createObservationUnitKey(importRow), variableName, importRow.getEnv());
    }

    public static String getVariableNameFromColumn(Column<?> column) {
        // TODO: timestamp stripping?
        return column.name();
    }

    // TODO: common validation stuff, could probably be moved somewhere more specific to validation
    public static void addRowError(String field, String errorMessage, ValidationErrors validationErrors, int rowNum) {
        ValidationError ve = new ValidationError(field, errorMessage, HttpStatus.UNPROCESSABLE_ENTITY);
        validationErrors.addError(rowNum + 2, ve);  // +2 instead of +1 to account for the column header row.
    }

    // TODO: will have different pending data objects between workflows so not totally reusable as-is
    // could probably just pass in actual underlying maps
    public static boolean isObservationMatched(ProcessedPhenotypeData phenotypeData,
                                               PendingData pendingData,
                                               String observationHash,
                                               String value,
                                               Column phenoCol,
                                               Integer rowNum) {
        Map<String, Column<?>> timeStampColByPheno = phenotypeData.getTimeStampColByPheno();

        if (timeStampColByPheno.isEmpty() || !timeStampColByPheno.containsKey(phenoCol.name())) {
            return isValueMatched(pendingData, observationHash, value);
        } else {
            String importObsTimestamp = timeStampColByPheno.get(phenoCol.name()).getString(rowNum);
            return isTimestampMatched(pendingData, observationHash, importObsTimestamp) && isValueMatched(pendingData, observationHash, value);
        }
    }

    // TODO: will have different pending data objects between workflows so not totally reusable as-is
    // could probably just pass in actual underlying maps
    public static boolean isValueMatched(PendingData pendingData, String observationHash, String value) {
        Map<String, BrAPIObservation> existingObsByObsHash = pendingData.getExistingObsByObsHash();

        if (!existingObsByObsHash.containsKey(observationHash) || existingObsByObsHash.get(observationHash).getValue() == null) {
            return value == null;
        }
        return existingObsByObsHash.get(observationHash).getValue().equals(value);
    }

    // TODO: will have different pending data objects between workflows so not totally reusable as-is
    // could probably just pass in actual underlying maps
    public static boolean isTimestampMatched(PendingData pendingData, String observationHash, String timeStamp) {
        OffsetDateTime priorStamp = null;
        Map<String, BrAPIObservation> existingObsByObsHash = pendingData.getExistingObsByObsHash();

        if(existingObsByObsHash.get(observationHash)!=null){
            priorStamp = existingObsByObsHash.get(observationHash).getObservationTimeStamp();
        }
        if (priorStamp == null) {
            return timeStamp == null;
        }
        boolean isMatched = false;
        try {
            isMatched = priorStamp.isEqual(OffsetDateTime.parse(timeStamp));
        } catch(DateTimeParseException e){
            //if timestamp is invalid DateTime not equal to validated priorStamp
            log.error(e.getMessage(), e);
        }
        return isMatched;
    }

    public static void validateObservationValue(Trait variable, String value,
                                          String columnHeader, ValidationErrors validationErrors, int row) {
        if (StringUtils.isBlank(value)) {
            log.debug(String.format("skipping validation of observation because there is no value.\n\tvariable: %s\n\trow: %d", variable.getObservationVariableName(), row));
            return;
        }

        if (isNAObservation(value)) {
            log.debug(String.format("skipping validation of observation because it is NA.\n\tvariable: %s\n\trow: %d", variable.getObservationVariableName(), row));
            return;
        }

        switch (variable.getScale().getDataType()) {
            case NUMERICAL:
                Optional<BigDecimal> number = validNumericValue(value);
                if (number.isEmpty()) {
                    addRowError(columnHeader, "Non-numeric text detected detected", validationErrors, row);
                } else if (!validNumericRange(number.get(), variable.getScale())) {
                    addRowError(columnHeader, "Value outside of min/max range detected", validationErrors, row);
                }
                break;
            case DATE:
                if (!validDateValue(value)) {
                    addRowError(columnHeader, "Incorrect date format detected. Expected YYYY-MM-DD", validationErrors, row);
                }
                break;
            case ORDINAL:
                if (!validCategory(variable.getScale().getCategories(), value)) {
                    addRowError(columnHeader, "Undefined ordinal category detected", validationErrors, row);
                }
                break;
            case NOMINAL:
                if (!validCategory(variable.getScale().getCategories(), value)) {
                    addRowError(columnHeader, "Undefined nominal category detected", validationErrors, row);
                }
                break;
            default:
                break;
        }

    }

    public static Optional<BigDecimal> validNumericValue(String value) {
        BigDecimal number;
        try {
            number = new BigDecimal(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(number);
    }

    public static boolean validNumericRange(BigDecimal value, Scale validValues) {
        // account for empty min or max in valid determination
        return (validValues.getValidValueMin() == null || value.compareTo(BigDecimal.valueOf(validValues.getValidValueMin())) >= 0) &&
                (validValues.getValidValueMax() == null || value.compareTo(BigDecimal.valueOf(validValues.getValidValueMax())) <= 0);
    }

    public static boolean validDateValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    public static boolean validCategory(List<BrAPIScaleValidValuesCategories> categories, String value) {
        Set<String> categoryValues = categories.stream()
                .map(category -> category.getValue().toLowerCase())
                .collect(Collectors.toSet());
        return categoryValues.contains(value.toLowerCase());
    }

    public static boolean isNAObservation(String value){
        return value.equalsIgnoreCase("NA");
    }

    public static void validateTimeStampValue(String value,
                                        String columnHeader, ValidationErrors validationErrors, int row) {
        if (StringUtils.isBlank(value)) {
            log.debug(String.format("skipping validation of observation timestamp because there is no value.\n\tvariable: %s\n\trow: %d", columnHeader, row));
            return;
        }
        if (!validDateValue(value) && !validDateTimeValue(value)) {
            addRowError(columnHeader, "Incorrect datetime format detected. Expected YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+hh:mm", validationErrors, row);
        }

    }

}