package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import com.fasterxml.jackson.annotation.JsonValue;

public class ExpImportProcessErrorConstants {

    public static final CharSequence COMMA_DELIMITER = ",";
    public enum ExpImportProcessErrMessage {

        MISSING_OBS_UNIT_ID_ERROR("Experimental entities are missing ObsUnitIDs"),
        PREEXISTING_EXPERIMENT_TITLE("Experiment Title already exists");
        private String value;

        ExpImportProcessErrMessage(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }

}
