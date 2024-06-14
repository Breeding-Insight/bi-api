package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.TrialService;

import javax.inject.Inject;

@Factory
public class PendingEntityFactory {
    private final TrialService trialService;
    private final BrAPITrialDAO brapiTrialDAO;
    private final BrAPIObservationUnitDAO observationUnitDAO;
    private final ObservationUnitService observationUnitService;
    private final ExperimentUtilities experimentUtilities;

    @Inject
    public PendingEntityFactory(TrialService trialService,
                                BrAPITrialDAO brapiTrialDAO,
                                BrAPIObservationUnitDAO observationUnitDAO,
                                ObservationUnitService observationUnitService,
                                ExperimentUtilities experimentUtilities) {
        this.trialService = trialService;
        this.brapiTrialDAO = brapiTrialDAO;
        this.observationUnitDAO = observationUnitDAO;
        this.observationUnitService = observationUnitService;
        this.experimentUtilities = experimentUtilities;
    }

    public static PendingTrial pendingTrial(ExpUnitMiddlewareContext context,
                                            TrialService trialService,
                                            BrAPITrialDAO brapiTrialDAO,
                                            ExperimentUtilities experimentUtilities) {
        return new PendingTrial(context, trialService, brapiTrialDAO, experimentUtilities);
    }

    public static PendingObservationUnit pendingObservationUnit(ExpUnitMiddlewareContext context,
                                            BrAPIObservationUnitDAO observationUnitDAO,
                                            ObservationUnitService observationUnitService,
                                            ExperimentUtilities experimentUtilities) {
        return new PendingObservationUnit(context, observationUnitDAO, observationUnitService, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingTrial pendingTrialBean(ExpUnitMiddlewareContext context) {
        return pendingTrial(context, trialService, brapiTrialDAO, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingObservationUnit pendingObservationUnitBean(ExpUnitMiddlewareContext context) {
        return pendingObservationUnit(context, observationUnitDAO, observationUnitService, experimentUtilities);
    }
}
