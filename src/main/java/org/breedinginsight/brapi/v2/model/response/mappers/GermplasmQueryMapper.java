package org.breedinginsight.brapi.v2.model.response.mappers;

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
                Map.entry("accessionNumber", BrAPIGermplasm::getAccessionNumber)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<BrAPIGermplasm, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) return fields.get(fieldName);
        else throw new NullPointerException();
    }
}
