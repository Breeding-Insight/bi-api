package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.model.ProgramLocation;

import javax.inject.Inject;

@Factory
public class BrAPIReadFactory {
    private final PendingEntityFactory pendingEntityFactory;

    @Inject
    public BrAPIReadFactory(PendingEntityFactory pendingEntityFactory) {
        this.pendingEntityFactory = pendingEntityFactory;
    }

    public static WorkflowReadInitialization<BrAPITrial> trialWorkflowReadInitialization(ExpUnitMiddlewareContext context,
                                                                                         PendingEntityFactory pendingEntityFactory) {
        return new WorkflowReadInitialization<BrAPITrial>(pendingEntityFactory.pendingTrialBean(context));
    }

    public static WorkflowReadInitialization<BrAPIObservationUnit> observationUnitWorkflowReadInitialization(ExpUnitMiddlewareContext context,
                                                                                                             PendingEntityFactory pendingEntityFactory) {
        return new WorkflowReadInitialization<BrAPIObservationUnit>(pendingEntityFactory.pendingObservationUnitBean(context));
    }

    public static WorkflowReadInitialization<BrAPIGermplasm> germplasmWorkflowReadInitialization(ExpUnitMiddlewareContext context,
                                                                                                 PendingEntityFactory pendingEntityFactory) {
        return new WorkflowReadInitialization<BrAPIGermplasm>(pendingEntityFactory.pendingGermplasmBean(context));
    }

    public static WorkflowReadInitialization<BrAPIListDetails> datasetWorkflowReadInitialization(ExpUnitMiddlewareContext context,
                                                                                                   PendingEntityFactory pendingEntityFactory) {
        return new WorkflowReadInitialization<BrAPIListDetails>(pendingEntityFactory.pendingDatasetBean(context));
    }

    public static WorkflowReadInitialization<BrAPIStudy> studyWorkflowReadInitialization(ExpUnitMiddlewareContext context,
                                                                                         PendingEntityFactory pendingEntityFactory) {
        return new WorkflowReadInitialization<BrAPIStudy>(pendingEntityFactory.pendingStudyBean(context));
    }

    public static WorkflowReadInitialization<ProgramLocation> locationWorkflowReadInitialization(ExpUnitMiddlewareContext context,
                                                                                              PendingEntityFactory pendingEntityFactory) {
        return new WorkflowReadInitialization<ProgramLocation>(pendingEntityFactory.pendingLocationBean(context));
    }

    @Bean
    @Prototype
    public WorkflowReadInitialization<BrAPITrial> trialWorkflowReadInitializationBean(ExpUnitMiddlewareContext context) {
        return trialWorkflowReadInitialization(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowReadInitialization<BrAPIObservationUnit> observationUnitWorkflowReadInitializationBean(ExpUnitMiddlewareContext context) {
        return observationUnitWorkflowReadInitialization(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowReadInitialization<BrAPIGermplasm> germplasmWorkflowReadInitializationBean(ExpUnitMiddlewareContext context) {
        return germplasmWorkflowReadInitialization(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowReadInitialization<BrAPIListDetails> datasetWorkflowReadInitializationBean(ExpUnitMiddlewareContext context) {
        return datasetWorkflowReadInitialization(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowReadInitialization<BrAPIStudy> studyWorkflowReadInitializationBean(ExpUnitMiddlewareContext context) {
        return studyWorkflowReadInitialization(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowReadInitialization<ProgramLocation> locationWorkflowReadInitializationBean(ExpUnitMiddlewareContext context) {
        return locationWorkflowReadInitialization(context, pendingEntityFactory);
    }
}
