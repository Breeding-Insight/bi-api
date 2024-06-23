package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPICreationFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPIUpdateFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowUpdate;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareError;

import javax.inject.Inject;
import java.util.Optional;

@Slf4j
@Prototype
public class BrAPIDatasetCommit extends AppendOverwriteMiddleware {
    private final BrAPICreationFactory brAPICreationFactory;
    private final BrAPIUpdateFactory brAPIUpdateFactory;
    private WorkflowCreation<BrAPIListDetails> datasetCreation;
    private WorkflowUpdate<BrAPIListDetails> datasetUpdate;
    private Optional<WorkflowCreation.BrAPICreationState> createdDatasets;
    private Optional<WorkflowUpdate.BrAPIUpdateState> priorDatasets;
    private Optional<WorkflowUpdate.BrAPIUpdateState> updatedDatasets;

    @Inject
    public BrAPIDatasetCommit(BrAPICreationFactory brAPICreationFactory, BrAPIUpdateFactory brAPIUpdateFactory) {
        this.brAPICreationFactory = brAPICreationFactory;
        this.brAPIUpdateFactory = brAPIUpdateFactory;
    }
    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {

        try {
            datasetCreation = brAPICreationFactory.datasetWorkflowCreationBean(context);
            log.info("creating new datasets in the BrAPI service");
            createdDatasets = datasetCreation.execute().map(s -> (WorkflowCreation.BrAPICreationState) s);
            datasetUpdate = brAPIUpdateFactory.datasetWorkflowUpdateBean(context);
            priorDatasets = datasetUpdate.getBrAPIState().map(d -> d);
            log.info("adding new observation variables to datasets");
            updatedDatasets = datasetUpdate.execute().map(d -> (WorkflowUpdate.BrAPIUpdateState) d);
        } catch (ApiException e) {
            context.getAppendOverwriteWorkflowContext().setProcessError(new MiddlewareError(e));
            return this.compensate(context);
        }
        return processNext(context);
    }

    @Override
    public AppendOverwriteMiddlewareContext compensate(AppendOverwriteMiddlewareContext context) {
        // Tag an error if it occurred in this local transaction
        context.getAppendOverwriteWorkflowContext().getProcessError().tag(this.getClass().getName());

        // Delete any created datasets
        createdDatasets.ifPresent(WorkflowCreation.BrAPICreationState::undo);

        // Revert any changes made to datasets in the BrAPI service
        priorDatasets.ifPresent(WorkflowUpdate.BrAPIUpdateState::restore);

        // Undo the prior local transaction
        return compensatePrior(context);
    }
}
