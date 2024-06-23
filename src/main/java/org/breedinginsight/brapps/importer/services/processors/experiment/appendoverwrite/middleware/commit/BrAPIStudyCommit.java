package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPICreationFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareError;

import javax.inject.Inject;
import java.util.Optional;

@Slf4j
@Prototype
public class BrAPIStudyCommit extends AppendOverwriteMiddleware {
    private BrAPICreationFactory brAPICreationFactory;
    private WorkflowCreation<BrAPIStudy> brAPIStudyCreation;
    private Optional<WorkflowCreation.BrAPICreationState> createdBrAPIStudies;

    @Inject
    public BrAPIStudyCommit(BrAPICreationFactory brAPICreationFactory) {
        this.brAPICreationFactory = brAPICreationFactory;
    }
    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        try {
            brAPIStudyCreation = brAPICreationFactory.studyWorkflowCreationBean(context);
            log.info("creating new studies in the BrAPI service");
            createdBrAPIStudies = brAPIStudyCreation.execute().map(s -> (WorkflowCreation.BrAPICreationState) s);
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

        // Delete any created studies from the BrAPI service
        createdBrAPIStudies.ifPresent(WorkflowCreation.BrAPICreationState::undo);

        // Undo the prior local transaction
        return compensatePrior(context);
    }
}
