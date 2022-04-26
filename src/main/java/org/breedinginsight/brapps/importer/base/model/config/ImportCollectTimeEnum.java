package org.breedinginsight.brapps.importer.base.model.config;

public enum ImportCollectTimeEnum {
    MAPPING("MAPPING"),
    UPLOAD("UPLOAD");

    private String value;

    ImportCollectTimeEnum(String value) {
        this.value = value;
    }
}
