package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;
import javax.inject.Provider;

@Slf4j
public class ImportPreviewStatistics extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;
    private Provider<TraitVerification> traitVerificationProvider;
    private Provider<NewPendingBrAPIObjects> newPendingBrAPIObjectsProvider;
    private Provider<DataValidation> dataValidationProvider;
    private Provider<FieldValidation> fieldValidationProvider;

    @Inject
    public ImportPreviewStatistics(Provider<TraitVerification> traitVerificationProvider,
                                   Provider<NewPendingBrAPIObjects> newPendingBrAPIObjectsProvider,
                                   Provider<DataValidation> dataValidationProvider,
                                   Provider<FieldValidation> fieldValidationProvider) {

        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(
                traitVerificationProvider.get(),        // Verify observation variable columns in import belong to program
                newPendingBrAPIObjectsProvider.get(),   // Construct Pending import objects for new BrAPI data
                dataValidationProvider.get(),           // Validate data
                fieldValidationProvider.get());         // Validate fields
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        log.debug("generating import preview statistics");
        return this.middleware.process(context);
    }
}
