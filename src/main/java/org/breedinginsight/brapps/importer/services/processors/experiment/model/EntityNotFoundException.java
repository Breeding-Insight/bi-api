package org.breedinginsight.brapps.importer.services.processors.experiment.model;

import lombok.Getter;

import java.util.Set;
@Getter
public class EntityNotFoundException extends Throwable {
    private Set<String> missingEntityIds;

    public EntityNotFoundException(Set<String> missingEntityIds) { this.missingEntityIds = missingEntityIds; }
}
