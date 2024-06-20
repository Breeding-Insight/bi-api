package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.update;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;

@Factory
public class BrAPIUpdateFactory {
    private final PendingEntityFactory pendingEntityFactory;

    @Inject
    public BrAPIUpdateFactory(PendingEntityFactory pendingEntityFactory) {
        this.pendingEntityFactory = pendingEntityFactory;
    }

    public static WorkflowUpdate<BrAPITrial> trialWorkflowUpdate(ExpUnitMiddlewareContext context,
                                                                 PendingEntityFactory pendingEntityFactory) {
        return new WorkflowUpdate<BrAPITrial>(pendingEntityFactory.pendingTrialBean(context));
    }

    public static WorkflowUpdate<BrAPIObservation> observationWorkflowUpdate(ExpUnitMiddlewareContext context,
                                                                       PendingEntityFactory pendingEntityFactory) {
        return new WorkflowUpdate<BrAPIObservation>(pendingEntityFactory.pendingObservationBean(context));
    }

    public static WorkflowUpdate<BrAPIListDetails> datasetWorkflowUpdate(ExpUnitMiddlewareContext context,
                                                                         PendingEntityFactory pendingEntityFactory) {
        return new WorkflowUpdate<BrAPIListDetails>(pendingEntityFactory.pendingDatasetBean(context));
    }

    @Bean
    @Prototype
    public WorkflowUpdate<BrAPITrial> trialWorkflowUpdateBean(ExpUnitMiddlewareContext context) {
        return trialWorkflowUpdate(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowUpdate<BrAPIObservation> observationWorkflowUpdateBean(ExpUnitMiddlewareContext context) {
        return observationWorkflowUpdate(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowUpdate<BrAPIListDetails> datasetWorkflowUpdateBean(ExpUnitMiddlewareContext context) {
        return datasetWorkflowUpdate(context, pendingEntityFactory);
    }
}
