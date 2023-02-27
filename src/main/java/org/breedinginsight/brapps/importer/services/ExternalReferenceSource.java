package org.breedinginsight.brapps.importer.services;

import lombok.Getter;

@Getter
public enum ExternalReferenceSource {
    PROGRAMS("programs"),
    TRIALS("trials"),
    STUDIES("studies"),
    OBSERVATION_UNITS("observationunits"),
    LISTS("lists");

    private String name;

    ExternalReferenceSource(String name) {
        this.name = name;
    }
}

