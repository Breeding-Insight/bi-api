package org.breedinginsight.brapi.v2.model.response.mappers;

import lombok.Getter;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.utilities.response.mappers.AbstractQueryMapper;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Getter
@Singleton
public class ListQueryMapper extends AbstractQueryMapper {

    private Map<String, Function<BrAPIGermplasm, ?>> fields;

    public ListQueryMapper() {
        fields = Map.ofEntries();
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
