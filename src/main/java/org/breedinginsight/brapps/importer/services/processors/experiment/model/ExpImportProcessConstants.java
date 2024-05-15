package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.context.annotation.Property;

public class ExpImportProcessConstants {

    public static final CharSequence COMMA_DELIMITER = ",";
    public static final String TIMESTAMP_PREFIX = "TS:";
    public static final String TIMESTAMP_REGEX = "^"+TIMESTAMP_PREFIX+"\\s*";
    public static final String MIDNIGHT = "T00:00:00-00:00";
    @Property(name = "brapi.server.reference-source")
    public static String BRAPI_REFERENCE_SOURCE;
    public enum ErrMessage {
        MULTIPLE_EXP_TITLES("File contains more than one Experiment Title"),
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
