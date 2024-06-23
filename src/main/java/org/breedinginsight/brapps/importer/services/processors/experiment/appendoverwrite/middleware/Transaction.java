package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;

@Slf4j
@Prototype
public class Transaction extends AppendOverwriteMiddleware {

    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        return processNext(context);
    }

    @Override
    public AppendOverwriteMiddlewareContext compensate(AppendOverwriteMiddlewareContext context) {

        return context;
    }
}
