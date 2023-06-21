package org.breedinginsight.model;

import com.google.gson.JsonObject;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import java.util.List;

public class Dataset {
    public String experimentId;
    public JsonObject additionalInfo;
    public List<BrAPIObservation> data;
    public List<BrAPIObservationUnit> observationUnits;
    public List<BrAPIObservationVariable> observationVariables;

    public Dataset(
            String experimentId,
            JsonObject additionalInfo,
            List<BrAPIObservation> data,
            List<BrAPIObservationUnit> observationUnits,
            List<BrAPIObservationVariable> observationVariables) {
        this.experimentId = experimentId;
        this.additionalInfo = additionalInfo;
        this.data = data;
        this.observationUnits = observationUnits;
        this.observationVariables = observationVariables;
    }
}
