package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import org.breedinginsight.brapps.importer.services.processors.experiment.middleware.Middleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

/**
 * ExpUnitMiddleware class extends Middleware class to handle compensating transactions in the context of ExpUnitMiddlewareContext.
 */
public abstract class ExpUnitMiddleware extends Middleware<ExpUnitMiddlewareContext> {

    /**
     * Compensates for an error that occurred in the current local transaction, tagging the error and undoing the previous local transaction.
     *
     * @param context The context in which the compensation is to be performed.
     * @param error The error that occurred and needs to be compensated.
     * @return True if the prior local transaction was successfully compensated, false otherwise.
     */
    @Override
    public boolean compensate(ExpUnitMiddlewareContext context, MiddlewareError error) {
        // tag an error if it occurred in this local transaction
        error.tag(this.getClass().getName());

        // undo the prior local transaction
        return compensatePrior(context, error);
    }
}
