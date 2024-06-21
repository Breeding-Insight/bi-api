package org.breedinginsight.brapps.importer.services.processors.experiment.factory.action;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.factory.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.model.ProgramLocation;

import javax.inject.Inject;

@Factory
public class BrAPICreationFactory {
    private final PendingEntityFactory pendingEntityFactory;

    @Inject
    public BrAPICreationFactory(PendingEntityFactory pendingEntityFactory) {
        this.pendingEntityFactory = pendingEntityFactory;
    }

    public static WorkflowCreation<BrAPITrial> trialWorkflowCreation(ExpUnitMiddlewareContext context,
                                                                     PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPITrial>(pendingEntityFactory.pendingTrialBean(context));
    }

    public static WorkflowCreation<BrAPIListDetails> datasetWorkflowCreation(ExpUnitMiddlewareContext context,
                                                                             PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIListDetails>(pendingEntityFactory.pendingDatasetBean(context));
    }

    public static WorkflowCreation<BrAPIStudy> studyWorkflowCreation(ExpUnitMiddlewareContext context,
                                                                     PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIStudy>(pendingEntityFactory.pendingStudyBean(context));
    }

    public static WorkflowCreation<BrAPIObservation> observationWorkflowCreation(ExpUnitMiddlewareContext context,
                                                                                 PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIObservation>(pendingEntityFactory.pendingObservationBean(context));
    }

    public static WorkflowCreation<BrAPIObservationUnit> observationUnitWorkflowCreation(ExpUnitMiddlewareContext context,
                                                                                         PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIObservationUnit>(pendingEntityFactory.pendingObservationUnitBean(context));
    }

    public static WorkflowCreation<ProgramLocation> locationWorkflowCreation(ExpUnitMiddlewareContext context,
                                                                             PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<ProgramLocation>(pendingEntityFactory.pendingLocationBean(context));
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPITrial> trialWorkflowCreationBean(ExpUnitMiddlewareContext context) {
        return trialWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPIListDetails> datasetWorkflowCreationBean(ExpUnitMiddlewareContext context) {
        return datasetWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPIStudy> studyWorkflowCreationBean(ExpUnitMiddlewareContext context) {
        return studyWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPIObservation> observationWorkflowCreationBean(ExpUnitMiddlewareContext context) {
        return observationWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPIObservationUnit> observationUnitWorkflowCreationBean(ExpUnitMiddlewareContext context) {
        return observationUnitWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<ProgramLocation> locationWorkflowCreationBean(ExpUnitMiddlewareContext context) {
        return locationWorkflowCreation(context, pendingEntityFactory);
    }
}
