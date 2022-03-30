package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

@NoArgsConstructor
public class ExperimentList{
    private HashMap<String, Experiment> expMap = new HashMap<>();

    public Experiment retrieve_or_add(String title, String description){
        String key = title + description;
        Experiment experiment = null;
        if( ! expMap.containsKey(key) ){
            experiment = new Experiment(title, description);
            expMap.put(key,experiment);
        }
        else {
            experiment = expMap.get(key);
        }
        return experiment;
    }

    public int size(){
        return expMap.size();
    }

    public Set<String> keySet(int i){
        return expMap.keySet();
    }

    public Experiment get(String key){
        return expMap.get(key);
    }

    public Collection<Experiment> values(){
        return expMap.values();
    }


}
