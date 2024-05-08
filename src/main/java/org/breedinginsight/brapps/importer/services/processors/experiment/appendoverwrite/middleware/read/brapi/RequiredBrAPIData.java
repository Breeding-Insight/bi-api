package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.read.brapi;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;
import javax.inject.Provider;

@Slf4j
public class RequiredBrAPIData extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;
    Provider<RequiredObservationUnits> existingObservationUnitsProvider;

    @Inject
    public RequiredBrAPIData(Provider<RequiredObservationUnits> existingObservationUnitsProvider) {
        this.middleware.link(existingObservationUnitsProvider.get());
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        log.debug("reading required BrAPI data from BrAPI service");
        return this.middleware.process(context);
    }
}
