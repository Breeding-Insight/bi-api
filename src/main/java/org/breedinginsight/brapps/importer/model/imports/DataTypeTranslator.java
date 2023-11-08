package org.breedinginsight.brapps.importer.model.imports;

import org.breedinginsight.dao.db.enums.DataType;

import java.util.Map;
import java.util.Optional;

public class DataTypeTranslator {

    private static final Map<String, DataType> displayToTypeMap = Map.of(
            "Date", DataType.DATE,
            "Numerical", DataType.NUMERICAL,
            "Nominal", DataType.NOMINAL,
            "Ordinal", DataType.ORDINAL,
            "Text", DataType.TEXT);


    public static Optional<DataType> getTypeFromUserDisplayName(String displayName) {
        return Optional.ofNullable(displayToTypeMap.get(displayName));
    }

    public static String getDisplayNameFromType(DataType type){
        String displayName = "";
        if(type!=null) {
            for (String key : displayToTypeMap.keySet()) {
                if (type == displayToTypeMap.get(key)) {
                    displayName = key;
                    break;
                }
            }
        }
        return displayName;
    }
}
