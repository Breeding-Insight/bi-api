package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
@Prototype
public class Transaction extends ExpUnitMiddleware {

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        return processNext(context);
    }

    @Override
    public ExpUnitMiddlewareContext compensate(ExpUnitMiddlewareContext context) {

        return context;
    }
}
