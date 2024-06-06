package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;

import javax.inject.Inject;

@Slf4j
public class Transaction extends ExpUnitMiddleware {
    ExpUnitMiddlewareContext middleware;

    @Inject
    public Transaction() {

    }
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        return processNext(context);
    }

    @Override
    public ExpUnitMiddlewareContext compensate(ExpUnitMiddlewareContext context, MiddlewareError error) {
        // TODO: handle any error here

        return context;
    }
}
