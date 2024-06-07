package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

@Slf4j
public class ExpUnitIDValidation extends ExpUnitMiddleware {
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {

        context.getExpUnitContext().setReferenceOUIds(ExperimentUtilities.collateReferenceOUIds(context));
        return processNext(context);
    }

    @Override
    public ExpUnitMiddlewareContext compensate(ExpUnitMiddlewareContext context, MiddlewareError error) {
        // tag an error if it occurred in this local transaction
        error.tag(this.getClass().getName());

        // undo the prior local transaction
        return compensatePrior(context, error);
    }


}
