package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@Getter
@Setter
public class EnvironmentData {
    private String env;
    private String location;
    private String year;
    // TODO we don't want a getObservationUnitList() method
    private List<ObservationUnitData> observationUnitDataList = new ArrayList<>();

    public EnvironmentData(String env, String location, String year) {
        this.env = env;
        this.location = location;
        this.year = year;
    }

    public void addObservationUnitData(ObservationUnitData ou){
        this.observationUnitDataList.add(ou);
    }

    public Collection<ObservationUnitData> observationUnitValues(){
        return this.observationUnitDataList;
    }
}
