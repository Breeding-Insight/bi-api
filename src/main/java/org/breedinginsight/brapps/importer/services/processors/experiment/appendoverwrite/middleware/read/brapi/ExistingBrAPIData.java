package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.read.brapi;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;
import javax.inject.Provider;

@Slf4j
public class ExistingBrAPIData extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;
    Provider<ExistingObservationUnits> existingObservationUnitsProvider;

    @Inject
    public ExistingBrAPIData(Provider<ExistingObservationUnits> existingObservationUnitsProvider) {
        this.middleware.link(existingObservationUnitsProvider.get());
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        return this.middleware.process(context);
    }
}
