package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;

@Slf4j
public class BrAPICommit extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;
    @Inject
    public BrAPICommit(ExpUnitMiddleware brAPITrialCreation) {
        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(brAPITrialCreation);
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        log.debug("starting post of experiment data to BrAPI server");



        return processNext(context);
    }
}
