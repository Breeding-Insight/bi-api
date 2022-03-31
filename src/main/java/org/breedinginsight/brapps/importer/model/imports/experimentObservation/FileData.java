package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

@NoArgsConstructor
public class FileData {
    private HashMap<String, ExperimentData> expMap = new HashMap<>();

    public ExperimentData retrieve_or_add_ExperimentData(String title, String description){
        String key = title + description;
        ExperimentData experimentData = null;
        if( ! expMap.containsKey(key) ){
            experimentData = new ExperimentData(title, description);
            expMap.put(key,experimentData);
        }
        else {
            experimentData = expMap.get(key);
        }
        return experimentData;
    }

    public int size(){
        return expMap.size();
    }

    public ExperimentData get_ExperimentData(String key){
        return expMap.get(key);
    }

    public Collection<ExperimentData> values(){
        return expMap.values();
    }


}
