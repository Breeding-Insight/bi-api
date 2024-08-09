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

    @Getter
    @Setter
    private ImportObjectState state;

    // Note: do not use @Inject, DeltaEntity<T> are always constructed by DeltaEntityFactory.
    Experiment(BrAPITrial brAPIObject) {
        super(brAPIObject);
    }

    public List<DatasetMetadata> getDatasetsMetadata() {
        List<DatasetMetadata> datasetsMetadata = new ArrayList<>();
        JsonObject additionalInfo = getEntity().getAdditionalInfo();
        if (additionalInfo != null) {
            JsonArray datasetsJson = additionalInfo.getAsJsonArray(BrAPIAdditionalInfoFields.DATASETS);
            if (datasetsJson != null) {
                datasetsMetadata = DatasetUtil.datasetsFromJson(datasetsJson);
            }
        }
        return datasetsMetadata;
    }

    public void setDatasetsMetadata(List<DatasetMetadata> datasetsMetadata) {
        if (datasetsMetadata != null) {
            JsonArray datasetsJson = DatasetUtil.jsonArrayFromDatasets(datasetsMetadata);
            getEntity().putAdditionalInfoItem(BrAPIAdditionalInfoFields.DATASETS, datasetsJson);
        }
    }

    public void addDatasetMetadata(DatasetMetadata datasetMetadata) {
        List<DatasetMetadata> datasetsMetadata = getDatasetsMetadata();
        datasetsMetadata.add(datasetMetadata);
        setDatasetsMetadata(datasetsMetadata);
    }

}
