package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

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

    @Bean
    @Prototype
    public WorkflowReadInitialization<BrAPITrial> trialWorkflowReadInitialization(ExpUnitMiddlewareContext context) {
        return trialWorkflowReadInitialization(context, pendingEntityFactory);
    }
}
