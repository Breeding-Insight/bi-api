package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import java.util.List;
import java.util.Optional;

public interface BrAPIAction<T> {
    Optional<BrAPIState> execute();
    ExperimentImportEntity<T> getEntity(ExpUnitMiddlewareContext context);
}
