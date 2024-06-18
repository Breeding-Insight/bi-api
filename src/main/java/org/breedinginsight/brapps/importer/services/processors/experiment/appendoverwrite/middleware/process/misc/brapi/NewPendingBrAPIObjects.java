package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.misc.brapi;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.ImportTableProcess;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;

@Slf4j
public class NewPendingBrAPIObjects extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;
    @Inject
    public NewPendingBrAPIObjects(ImportTableProcess importTableProcess) {
        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(importTableProcess); // Construct new pending observation
    }

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        log.debug("constructing new pending BrAPI objects");


        return processNext(context);
    }
}
