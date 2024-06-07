package org.breedinginsight.brapps.importer.services.processors.experiment.workflow;

import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.GetExistingBrAPIData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.Transaction;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitIDValidation;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ProcessedData;

import javax.inject.Inject;


@Prototype
public class AppendOverwritePhenotypesWorkflow implements Workflow {

    ExpUnitMiddleware middleware;
    @Inject
    public AppendOverwritePhenotypesWorkflow(Transaction transaction,
                                             ExpUnitIDValidation expUnitIDValidation,
                                             GetExistingBrAPIData getExistingBrAPIData) {

        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(transaction, expUnitIDValidation, getExistingBrAPIData);
    }
    @Override
    public ProcessedData process(ImportContext context) {
        ExpUnitMiddlewareContext workflowContext = ExpUnitMiddlewareContext.builder().importContext(context).build();
        this.middleware.process(workflowContext);

        // TODO: implement
        return new ProcessedData();
    }

    @Override
    public String getName() {
        return "AppendOverwritePhenotypesWorkflow";
    }

}
