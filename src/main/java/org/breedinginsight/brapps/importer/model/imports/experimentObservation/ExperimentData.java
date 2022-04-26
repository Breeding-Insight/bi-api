package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


public class ExperimentData {
    @Getter
    @Setter
    private String title;
    @Getter
    @Setter
    private String description;


    private Map<String, EnvironmentData> environments = new HashMap<>();

    public ExperimentData(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public EnvironmentData retrieve_or_add_environmentData(String envName, String envLoc, String envYear){
        String key = envLoc + envYear;
        EnvironmentData environmentData;
        if( ! environments.containsKey(key) ){
            environmentData = new EnvironmentData(envName, envLoc, envYear);
            environments.put(key, environmentData);
        }
        else{
            environmentData = this.environments.get(key);
        }
        return environmentData;
    }

    public Collection<EnvironmentData> environmentData_values(){
        return environments.values();
    }
}

