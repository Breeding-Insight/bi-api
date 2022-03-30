package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@Getter
@Setter
public class Environment {
    private String env;
    private String location;
    private String year;
    // TODO we don't want a getObservationUnitList() method
    private List<ObservationUnit> observationUnitList = new ArrayList<>();

    public Environment(String env, String location, String year) {
        this.env = env;
        this.location = location;
        this.year = year;
    }

    public void addObservationUnit(ObservationUnit ou){
        this.observationUnitList.add(ou);
    }

    public Collection<ObservationUnit> observationUnitValues(){
        return this.observationUnitList;
    }
}
