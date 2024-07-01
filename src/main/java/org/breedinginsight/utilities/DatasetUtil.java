package org.breedinginsight.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import org.brapi.client.v2.JSON;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.model.DatasetLevel;
import org.breedinginsight.model.DatasetMetadata;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DatasetUtil {

    public static Type listDatasetType = new TypeToken<List<DatasetMetadata>>() {}.getType();
    public static Gson gson = new JSON().getGson();

    public static List<DatasetMetadata> datasetsFromJson(JsonArray datasetsJsonArray) {
        if (datasetsJsonArray != null) {
            return gson.fromJson(datasetsJsonArray, listDatasetType);
        }

        return new ArrayList<>();
    }

    public static JsonArray jsonArrayFromDatasets(List<DatasetMetadata> datasets) {
        return gson.toJsonTree(datasets, listDatasetType).getAsJsonArray();
    }

    public static String getDatasetIdByNameFromJson(JsonArray datasetsJsonArray, String datasetName) {
        DatasetMetadata dataset = getDatasetByNameFromJson(datasetsJsonArray, datasetName);
        if (dataset != null) {
            return dataset.getId().toString();
        }
        return null;
    }

    public static DatasetMetadata getDatasetByNameFromJson(JsonArray datasetsJsonArray, String datasetName) {
        // Short-circuiting null check.
        if (datasetsJsonArray == null || datasetName == null) {
            return null;
        }
        List<DatasetMetadata> datasets = datasetsFromJson(datasetsJsonArray);
        for (DatasetMetadata dataset : datasets) {
            if (dataset.getName().equals(datasetName)) {
                return dataset;
            }
        }
        return null;
    }

    public static DatasetMetadata getTopLevelDatasetFromJson(JsonArray datasetsJsonArray) {
        List<DatasetMetadata> datasets = datasetsFromJson(datasetsJsonArray);
        // Return the top level dataset if it exists, otherwise null.
        return datasets.stream().filter(dataset -> dataset.getLevel().equals(DatasetLevel.EXP_UNIT)).findFirst().orElse(null);
    }

    public static DatasetMetadata getTopLevelDataset(BrAPITrial experiment) {
        return getTopLevelDatasetFromJson(experiment.getAdditionalInfo().getAsJsonArray(BrAPIAdditionalInfoFields.DATASETS));
    }

}
