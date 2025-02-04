package org.breedinginsight.brapps.importer.services.processors.experiment;

import lombok.Getter;

import java.util.Set;

@Getter
public class MissingValuesException extends Exception {
    private Set<String> missingIds;

    public MissingValuesException(Set<String> missingIds) { this.missingIds = missingIds; }
}
