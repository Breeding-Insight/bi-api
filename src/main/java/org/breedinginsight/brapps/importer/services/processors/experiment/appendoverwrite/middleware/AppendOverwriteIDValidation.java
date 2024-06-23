package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;

@Slf4j
@Prototype
public class AppendOverwriteIDValidation extends AppendOverwriteMiddleware {
    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {

        context.getAppendOverwriteWorkflowContext().setReferenceOUIds(ExperimentUtilities.collateReferenceOUIds(context));
        return processNext(context);
    }

    @Override
    public AppendOverwriteMiddlewareContext compensate(AppendOverwriteMiddlewareContext context) {
        // tag an error if it occurred in this local transaction
        context.getAppendOverwriteWorkflowContext().getProcessError().tag(this.getClass().getName());

        // undo the prior local transaction
        return compensatePrior(context);
    }


}
