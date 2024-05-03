package org.breedinginsight.brapps.importer.services.processors.experiment;

import com.google.gson.JsonObject;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExperimentUtilities {

    public static final CharSequence COMMA_DELIMITER = ",";
    public static final String TIMESTAMP_PREFIX = "TS:";

    public static List<ExperimentObservation> importRowsToExperimentObservations(List<BrAPIImport> importRows) {
        return importRows.stream()
                .map(trialImport -> (ExperimentObservation) trialImport)
                .collect(Collectors.toList());
    }

    public static String createObservationUnitKey(ExperimentObservation importRow) {
        return createObservationUnitKey(importRow.getEnv(), importRow.getExpUnitId());
    }

    public static String createObservationUnitKey(String studyName, String obsUnitName) {
        return studyName + obsUnitName;
    }

    /**
     * Returns the single value from the given map, throwing an UnprocessableEntityException if the map does not contain exactly one entry.
     *
     * @param map The map from which to retrieve the single value.
     * @param message The error message to include in the UnprocessableEntityException if the map does not contain exactly one entry.
     * @return The single value from the map.
     * @throws UnprocessableEntityException if the map does not contain exactly one entry.
     */
    public <K, V> V getSingleEntryValue(Map<K, V> map, String message) throws UnprocessableEntityException {
        if (map.size() != 1) {
            throw new UnprocessableEntityException(message);
        }
        return map.values().iterator().next();
    }

    /*
     * this will add the given year to the additionalInfo field of the BrAPIStudy (if it does not already exist)
     * */
    public void addYearToStudyAdditionalInfo(Program program, BrAPIStudy study, String year) {
        JsonObject additionalInfo = study.getAdditionalInfo();
        if (additionalInfo==null){
            additionalInfo = new JsonObject();
            study.setAdditionalInfo(additionalInfo);
        }
        if( additionalInfo.get(BrAPIAdditionalInfoFields.ENV_YEAR)==null) {
            additionalInfo.addProperty(BrAPIAdditionalInfoFields.ENV_YEAR, year);
        }
    }
}
