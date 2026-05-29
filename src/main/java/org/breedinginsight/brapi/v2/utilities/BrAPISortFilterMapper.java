package org.breedinginsight.brapi.v2.utilities;

import java.util.Map;

public abstract class BrAPISortFilterMapper {

    private final  Map<String, String> fields;

    protected BrAPISortFilterMapper(Map<String, String> fields) {
        this.fields = Map.copyOf(fields);
    }

    public boolean exists(String fieldName) {
        return fields.containsKey(fieldName);
    }

    public String getBrAPIName(String biFieldName) throws NullPointerException {
        String value = fields.get(biFieldName);

        if (value == null) {
            throw new IllegalArgumentException(
                    "No BrAPI mapping exists for field: " + biFieldName
            );
        }

        return value;
    }
}
