package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;

@Slf4j
@Prototype
public class BrAPICommit extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;
    @Inject
    public BrAPICommit(BrAPIDatasetCommit brAPIDatasetCommit,
                       BrAPITrialCommit brAPITrialCommit,
                       LocationCommit locationCommit,
                       BrAPIStudyCommit brAPIStudyCommit,
                       BrAPIObservationUnitCommit brAPIObservationUnitCommit,
                       BrAPIObservationCommit brAPIObservationCommit) {

        // TODO: add methods to entity/action classes to register and watch for required foreign key values so order does not have to be hard-wired
        // Note: the order is important because system-generated dbIds from prior steps are used as foreign keys in
        // subsequent steps
        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(
                brAPIDatasetCommit,
                brAPITrialCommit,
                locationCommit,
                brAPIStudyCommit,
                brAPIObservationUnitCommit,
                brAPIObservationCommit);
    }

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        log.debug("starting post of experiment data to BrAPI server");

        return this.middleware.process(context);
    }
}
