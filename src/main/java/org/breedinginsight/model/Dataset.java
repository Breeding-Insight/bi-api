
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


package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.JsonObject;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class Dataset {
    public String id;
    public String experimentId;
    public JsonObject additionalInfo;
    public List<BrAPIObservation> data;
    public List<BrAPIObservationUnit> observationUnits;
    public List<Trait> observationVariables;

    public enum DatasetStat {
        OBSERVATION_UNITS("observationUnits"),
        PHENOTYPES("phenotypes"),
        OBSERVATIONS("observations"),
        OBSERVATIONS_WITH_DATA("observationsWithData"),
        OBSERVATIONS_WITHOUT_DATA("observationsWithoutData");

        private final String displayName;
        DatasetStat(String value) {
            this.displayName = value;
        }

    }

    public Dataset(
            String id,
            String experimentId,
            List<BrAPIObservation> data,
            List<BrAPIObservationUnit> observationUnits,
            List<Trait> observationVariables) {
        this.id = id;
        this.experimentId = experimentId;
        this.additionalInfo = new JsonObject();
        this.data = data;
        this.observationUnits = observationUnits;
        this.observationVariables = observationVariables;
    }

    public Dataset setStat(DatasetStat stat, Integer count) {
        this.additionalInfo.addProperty(stat.displayName, count);
        return this;
    }
}
