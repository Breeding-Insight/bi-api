package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.TrialService;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class PendingTrialProvider implements Provider<PendingTrial> {
    TrialService trialService;
    BrAPITrialDAO brapiTrialDAO;
    ExperimentUtilities experimentUtilities;

    @Inject
    public PendingTrialProvider(TrialService trialService,
                        BrAPITrialDAO brAPITrialDAO,
                        ExperimentUtilities experimentUtilities) {
        this.trialService = trialService;
        this.brapiTrialDAO = brAPITrialDAO;
        this.experimentUtilities = experimentUtilities;
    }


//    public PendingTrial get(ExpUnitMiddlewareContext context) {
//        return new PendingTrial(context, trialService, brapiTrialDAO, experimentUtilities);
//    }

    /**
     * Provides a fully-constructed and injected instance of {@code T}.
     *
     * @throws RuntimeException if the injector encounters an error while
     *                          providing an instance. For example, if an injectable member on
     *                          {@code T} throws an exception, the injector may wrap the exception
     *                          and throw it to the caller of {@code get()}. Callers should not try
     *                          to handle such exceptions as the behavior may vary across injector
     *                          implementations and even different configurations of the same injector.
     */
    @Override
    public PendingTrial get() {
        return null;
    }
}
