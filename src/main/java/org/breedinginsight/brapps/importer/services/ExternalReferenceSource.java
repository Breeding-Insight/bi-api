package org.breedinginsight.brapps.importer.services;

import lombok.Getter;

@Getter
public enum ExternalReferenceSource {
    PROGRAMS("programs"),
    TRIALS("trials"),
    STUDIES("studies"),
    OBSERVATION_UNITS("observationunits"),
    DATASET("dataset"),
    LISTS("lists"),
    OBSERVATIONS("observations");

    private String name;

    ExternalReferenceSource(String name) {
        this.name = name;
    }
}

