package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

@Slf4j
public class Transaction extends ExpUnitMiddleware {
    // TODO: add member for ExpUnitContext

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        return processNext(context);
    }

    @Override
    public boolean compensate(ExpUnitMiddlewareContext context, MiddlewareError error) {
        // TODO: handle any error here

        return true;
    }
}
