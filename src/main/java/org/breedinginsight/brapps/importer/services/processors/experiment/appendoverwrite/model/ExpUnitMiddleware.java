package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model;

import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

/**
 * ExpUnitMiddleware class extends Middleware class to handle compensating transactions in the context of ExpUnitMiddlewareContext.
 */
public abstract class ExpUnitMiddleware extends Middleware<ExpUnitMiddlewareContext> {

    /**
     * Compensates for an error that occurred in the current local transaction, tagging the error and undoing the previous local transaction.
     *
     * @param context The context in which the compensation is to be performed.
     * @return True if the prior local transaction was successfully compensated, false otherwise.
     */
    @Override
    public ExpUnitMiddlewareContext compensate(ExpUnitMiddlewareContext context) {
        // tag an error if it occurred in this local transaction
        context.getExpUnitContext().getProcessError().tag(this.getClass().getName());

        // undo the prior local transaction
        return compensatePrior(context);
    }
}
