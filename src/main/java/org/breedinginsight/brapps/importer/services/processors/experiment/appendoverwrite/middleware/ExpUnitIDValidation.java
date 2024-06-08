package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
@Prototype
public class ExpUnitIDValidation extends ExpUnitMiddleware {
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {

        context.getExpUnitContext().setReferenceOUIds(ExperimentUtilities.collateReferenceOUIds(context));
        return processNext(context);
    }

    @Override
    public ExpUnitMiddlewareContext compensate(ExpUnitMiddlewareContext context) {
        // tag an error if it occurred in this local transaction
        context.getExpUnitContext().getProcessError().tag(this.getClass().getName());

        // undo the prior local transaction
        return compensatePrior(context);
    }


}
