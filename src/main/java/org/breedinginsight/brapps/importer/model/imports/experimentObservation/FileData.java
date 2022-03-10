package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor
public class FileData {
    private Map<String, ExperimentData> expMap = new HashMap<>();
    private Map<String, String> gids = new HashMap<>();

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

    public int experiments_size(){
        return expMap.size();
    }

    public ExperimentData get_ExperimentData(String key){
        return expMap.get(key);
    }

    public Collection<ExperimentData> experimentData(){
        return expMap.values();
    }

    public void add_gid(String gid){
        if(!gids.containsKey(gid)){
            gids.put(gid,gid);
        }
    }

    public int gids_size(){ return gids.size(); }


}
