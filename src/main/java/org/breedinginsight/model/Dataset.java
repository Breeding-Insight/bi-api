package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.gson.JsonObject;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class Dataset {
    public String experimentId;
    public JsonObject additionalInfo;
    public List<BrAPIObservation> data;
    public List<BrAPIObservationUnit> observationUnits;
    public List<BrAPIObservationVariable> observationVariables;

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
            String experimentId,
            List<BrAPIObservation> data,
            List<BrAPIObservationUnit> observationUnits,
            List<BrAPIObservationVariable> observationVariables) {
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
