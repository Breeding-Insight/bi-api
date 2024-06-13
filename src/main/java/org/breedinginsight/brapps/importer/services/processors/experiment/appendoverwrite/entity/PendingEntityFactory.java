package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.TrialService;

import javax.inject.Inject;

@Factory
public class PendingEntityFactory {
    private final TrialService trialService;
    private final BrAPITrialDAO brapiTrialDAO;
    private final ExperimentUtilities experimentUtilities;

    @Inject
    public PendingEntityFactory(TrialService trialService,
                                BrAPITrialDAO brapiTrialDAO,
                                ExperimentUtilities experimentUtilities) {
        this.trialService = trialService;
        this.brapiTrialDAO = brapiTrialDAO;
        this.experimentUtilities = experimentUtilities;
    }

    public static PendingTrial pendingTrial(ExpUnitMiddlewareContext context,
                                            TrialService trialService,
                                            BrAPITrialDAO brapiTrialDAO,
                                            ExperimentUtilities experimentUtilities) {
        return new PendingTrial(context, trialService, brapiTrialDAO, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingTrial pendingTrialBean(ExpUnitMiddlewareContext context) {
        return pendingTrial(context, trialService, brapiTrialDAO, experimentUtilities);
    }
}
