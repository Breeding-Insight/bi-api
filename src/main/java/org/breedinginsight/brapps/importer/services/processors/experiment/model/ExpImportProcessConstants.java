package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.context.annotation.Property;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExpImportProcessConstants {

    public static final CharSequence COMMA_DELIMITER = ",";
    public static final String TIMESTAMP_PREFIX = "TS:";
    public static final String TIMESTAMP_REGEX = "^"+TIMESTAMP_PREFIX+"\\s*";
    public static String BRAPI_REFERENCE_SOURCE;
    public static final String MIDNIGHT = "T00:00:00-00:00";

    public enum ErrMessage {
        MULTIPLE_EXP_TITLES("File contains more than one Experiment Title"),
        MISSING_OBS_UNIT_ID("Invalid ObsUnitID"),
        PREEXISTING_EXPERIMENT_TITLE("Experiment Title already exists"),
        UNMATCHED_COLUMN("Ontology term(s) not found: "),
        OBS_UNIT_NOT_FOUND("Invalid ObsUnitID"),
        DUPLICATE_OBS_UNIT_ID("ObsUnitId is repeated");

        private String value;

        ErrMessage(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

}
