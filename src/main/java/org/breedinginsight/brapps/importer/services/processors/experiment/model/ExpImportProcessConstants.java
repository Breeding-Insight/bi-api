package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import com.fasterxml.jackson.annotation.JsonValue;

public class ExpImportProcessConstants {

    public static final CharSequence COMMA_DELIMITER = ",";
    public static final String TIMESTAMP_PREFIX = "TS:";
    public static final String TIMESTAMP_REGEX = "^"+TIMESTAMP_PREFIX+"\\s*";
    public enum ErrMessage {
        MISSING_OBS_UNIT_ID_ERROR("Experimental entities are missing ObsUnitIDs"),
        PREEXISTING_EXPERIMENT_TITLE("Experiment Title already exists");

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
