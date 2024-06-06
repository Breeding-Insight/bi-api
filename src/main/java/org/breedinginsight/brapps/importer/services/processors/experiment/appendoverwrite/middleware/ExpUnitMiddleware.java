package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import org.breedinginsight.brapps.importer.services.processors.experiment.middleware.Middleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

public abstract class ExpUnitMiddleware extends Middleware<ExpUnitMiddlewareContext> {
    @Override
    public boolean compensate(ExpUnitMiddlewareContext context, MiddlewareError error) {
        // tag an error if it occurred in this local transaction
        error.tag(this.getClass().getName());

        // undo the prior local transaction
        return compensatePrior(context, error);
    }
}
