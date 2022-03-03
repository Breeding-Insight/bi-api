package org.breedinginsight.brapi.v2.model.response.mappers;

import com.google.gson.JsonObject;
import lombok.Getter;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.brapi.v1.model.ObservationVariable;
import org.breedinginsight.utilities.response.mappers.AbstractQueryMapper;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Getter
@Singleton
public class GermplasmQueryMapper extends AbstractQueryMapper {

    private String defaultSortField = "accessionNumber";
    private SortOrder defaultSortOrder = SortOrder.ASC;

    private Map<String, Function<BrAPIGermplasm, ?>> fields;

    public GermplasmQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("accessionNumber", BrAPIGermplasm::getAccessionNumber),
                Map.entry("defaultDisplayName", BrAPIGermplasm::getDefaultDisplayName),
                Map.entry("additionalInfo.breedingMethod", (germplasm) ->
                        germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().get("breedingMethod") != null ?
                                germplasm.getAdditionalInfo().get("breedingMethod").getAsString() : null),
                Map.entry("seedSource", BrAPIGermplasm::getSeedSource),
                Map.entry("femaleParent", (germplasm) ->
                        germplasm.getPedigree() != null ? germplasm.getPedigree().split("/")[0] : null),
                Map.entry("maleParent", (germplasm) ->
                        germplasm.getPedigree() != null && germplasm.getPedigree().split("/").length > 1 ? germplasm.getPedigree().split("/")[1] : null),
                Map.entry("additionalInfo.createdDate", (germplasm) ->
                        germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().get("createdDate") != null ?
                                germplasm.getAdditionalInfo().get("createdDate").getAsString() : null),
                Map.entry("additionalInfo.createdBy.userName", (germplasm) -> {
                            JsonObject additionalInfo = germplasm.getAdditionalInfo();
                            if (additionalInfo == null) { return null;}
                            if (!additionalInfo.has("createdBy")) { return null;}
                            JsonObject createdBy = additionalInfo.getAsJsonObject("createdBy");
                            if (!createdBy.has("userName")) { return null; }
                            return createdBy.get("userName").getAsString();
                        })
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<BrAPIGermplasm, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) {
            return fields.get(fieldName);
        }
        else {
            throw new NullPointerException();
        }
    }
}
