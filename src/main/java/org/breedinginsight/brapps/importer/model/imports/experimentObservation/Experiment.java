package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class Experiment {
    private String title;
    private String description;
//    private List<String> defaultObsevationLevels;
    private Map<String, Environment> environments = new HashMap<>();

    public Experiment(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public Environment addEnviroment(String envName, String envLoc, String envYear){
        String key = envLoc + envYear;
        Environment environment;
        if( ! environments.containsKey(key) ){
            environment = new Environment(envName, envLoc, envYear);
            environments.put(key, environment);
        }
        else{
            environment = this.environments.get(key);
        }
        return environment;
    }
}


