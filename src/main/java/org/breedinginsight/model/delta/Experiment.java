package org.breedinginsight.model.delta;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Prototype;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.model.DatasetMetadata;
import org.breedinginsight.utilities.DatasetUtil;

import java.util.ArrayList;
import java.util.List;

@Prototype
public class Experiment extends DeltaEntity<BrAPITrial> {

    @NonNull
    @Getter
    @Setter
    private ImportObjectState state;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    Experiment(BrAPITrial brAPIObject) {
        super(brAPIObject);
    }

    @Override
    public JsonObject getAdditionalInfo() {
        JsonObject additionalInfo = getBrAPIObject().getAdditionalInfo();
        if (additionalInfo == null) {
            additionalInfo = new JsonObject();
        }
        return additionalInfo;
    }

    @Override
    public void setAdditionalInfo(JsonObject additionalInfo) {
        getBrAPIObject().setAdditionalInfo(additionalInfo);
    }

    @Override
    public void putAdditionalInfoItem(String key, Object value) {
        getBrAPIObject().putAdditionalInfoItem(key, value);
    }

    public List<DatasetMetadata> getDatasetsMetadata() {
        List<DatasetMetadata> datasetsMetadata = new ArrayList<>();
        JsonArray datasetsJson = getAdditionalInfo().getAsJsonArray(BrAPIAdditionalInfoFields.DATASETS);
        if (datasetsJson != null) {
            datasetsMetadata = DatasetUtil.datasetsFromJson(datasetsJson);
        }
        return datasetsMetadata;
    }

    public void setDatasetsMetadata(List<DatasetMetadata> datasetsMetadata) {
        if (datasetsMetadata != null) {
            JsonArray datasetsJson = DatasetUtil.jsonArrayFromDatasets(datasetsMetadata);
            getBrAPIObject().putAdditionalInfoItem(BrAPIAdditionalInfoFields.DATASETS, datasetsJson);
        }
    }

    public void addDatasetMetadata(DatasetMetadata datasetMetadata) {
        List<DatasetMetadata> datasetsMetadata = getDatasetsMetadata();
        datasetsMetadata.add(datasetMetadata);
        setDatasetsMetadata(datasetsMetadata);
    }
}
