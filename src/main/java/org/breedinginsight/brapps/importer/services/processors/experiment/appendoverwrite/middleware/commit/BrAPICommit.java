package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;

import javax.inject.Inject;

@Slf4j
@Prototype
public class BrAPICommit extends AppendOverwriteMiddleware {
    AppendOverwriteMiddleware middleware;
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
        this.middleware = (AppendOverwriteMiddleware) AppendOverwriteMiddleware.link(
                brAPIDatasetCommit,
                brAPITrialCommit,
                locationCommit,
                brAPIStudyCommit,
                brAPIObservationUnitCommit,
                brAPIObservationCommit);
    }

    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        log.debug("starting post of experiment data to BrAPI server");

        return this.middleware.process(context);
    }
}
