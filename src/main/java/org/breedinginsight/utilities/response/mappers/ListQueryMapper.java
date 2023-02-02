package org.breedinginsight.utilities.response.mappers;

import lombok.Getter;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.utilities.response.mappers.AbstractQueryMapper;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Getter
@Singleton
public class ListQueryMapper extends AbstractQueryMapper {

    private Map<String, Function<BrAPIListSummary, ?>> fields;

    public ListQueryMapper() {
        fields = Map.ofEntries(
                Map.entry("name", BrAPIListSummary::getListName),
                Map.entry("description", BrAPIListSummary::getListDescription),
                Map.entry("size", BrAPIListSummary::getListSize),
                Map.entry("dateCreated", BrAPIListSummary::getDateCreated),
                Map.entry("ownerName", BrAPIListSummary::getListOwnerName),
                Map.entry("type", BrAPIListSummary::getListType)
        );
    }

    @Override
    public boolean exists(String fieldName) {
        return getFields().containsKey(fieldName);
    }

    @Override
    public Function<BrAPIListSummary, ?> getField(String fieldName) throws NullPointerException {
        if (fields.containsKey(fieldName)) {
            return fields.get(fieldName);
        }
        else {
            throw new NullPointerException();
        }
    }
}
