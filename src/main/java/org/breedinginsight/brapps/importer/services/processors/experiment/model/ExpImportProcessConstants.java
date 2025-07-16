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

package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExpImportProcessConstants {

    public static final CharSequence COMMA_DELIMITER = ",";
    public static final String OBSERVATION_UNIT_ID_SUFFIX = "ObsUnitID";
    public static final String TIMESTAMP_PREFIX = "TS:";
    public static final String TIMESTAMP_REGEX = "^"+TIMESTAMP_PREFIX+"\\s*";
    public static String BRAPI_REFERENCE_SOURCE;
    public static final String MIDNIGHT = "T00:00:00-00:00";
    public static final String SUB_UNIT_ID = "Sub Unit ID";
    public static final String SUB_OBS_UNIT = "Sub-Obs Unit";

    public enum ErrMessage {
        MULTIPLE_EXP_TITLES("File contains more than one Experiment Title"),
        MISSING_OBS_UNIT_ID("Invalid ObsUnitID"),
        PREEXISTING_EXPERIMENT_TITLE("Experiment Title already exists"),
        UNMATCHED_COLUMN("Ontology term(s) not found: "),
        OBS_UNIT_NOT_FOUND("Invalid ObsUnitID"),
        DUPLICATE_OBS_UNIT_ID("ObsUnitId is repeated"),
        DATASET_NOT_FOUND("Dataset not found"),
        OZEX("Missing ObsUnitID column"),
        VVCN("ObsUnitID is duplicated"),
        BITB("Invalid or missing ObsUnitID"),
        PJZH("Required field is blank"),
        JABH("Observation variable(s) are already associated with another dataset(s) in this experiment");

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
