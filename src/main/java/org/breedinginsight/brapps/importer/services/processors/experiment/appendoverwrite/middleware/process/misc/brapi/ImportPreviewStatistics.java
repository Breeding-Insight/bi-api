package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.misc.brapi;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.misc.brapi.FieldValidation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.misc.brapi.NewPendingBrAPIObjects;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;
import javax.inject.Provider;

@Slf4j
public class ImportPreviewStatistics extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;
    private Provider<NewPendingBrAPIObjects> newPendingBrAPIObjectsProvider;
    private Provider<FieldValidation> fieldValidationProvider;

    @Inject
    public ImportPreviewStatistics(Provider<NewPendingBrAPIObjects> newPendingBrAPIObjectsProvider,
                                   Provider<FieldValidation> fieldValidationProvider) {

        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(
                newPendingBrAPIObjectsProvider.get(),   // Construct Pending import objects for new BrAPI data
                fieldValidationProvider.get());         // Validate fields
    }

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        log.debug("generating import preview statistics");
        return this.middleware.process(context);
    }
}
