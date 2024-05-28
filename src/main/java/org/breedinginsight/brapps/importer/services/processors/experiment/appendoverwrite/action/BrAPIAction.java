package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import java.util.Optional;

public interface BrAPIAction<T> {
    Optional<BrAPIState> execute() throws ApiException;
    ExperimentImportEntity<T> getEntity(ExpUnitMiddlewareContext context);
}
