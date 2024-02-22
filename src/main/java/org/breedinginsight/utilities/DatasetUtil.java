package org.breedinginsight.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import org.brapi.client.v2.JSON;
import org.breedinginsight.model.DatasetMetadata;

import java.lang.reflect.Type;
import java.util.List;

public class DatasetUtil {

    public static Type listDatasetType = new TypeToken<List<DatasetMetadata>>() {}.getType();
    public static Gson gson = new JSON().getGson();

    public static List<DatasetMetadata> datasetsFromJson(JsonArray datasetsJsonArray) {
        if (datasetsJsonArray != null) {
            return gson.fromJson(datasetsJsonArray, listDatasetType);
        }

        return List.of();
    }

    public static JsonArray jsonArrayFromDatasets(List<DatasetMetadata> datasets) {
        return gson.toJsonTree(datasets, listDatasetType).getAsJsonArray();
    }
}
