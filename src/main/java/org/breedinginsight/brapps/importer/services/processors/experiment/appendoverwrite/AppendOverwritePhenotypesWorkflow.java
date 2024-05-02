package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite;

import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapps.importer.services.processors.experiment.middleware.ExpUnit.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.middleware.ExpUnit.GetExistingBrAPIData;
import org.breedinginsight.brapps.importer.services.processors.experiment.middleware.ExpUnit.ValidateAllRowsHaveIDs;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.workflow.Workflow;

@Prototype
public class AppendOverwritePhenotypesWorkflow implements Workflow {

    ExpUnitMiddleware middleware;
    GetExistingBrAPIData getExistingBrAPIData;
    public AppendOverwritePhenotypesWorkflow(GetExistingBrAPIData getExistingBrAPIData) {
        this.middleware.link(
                new ValidateAllRowsHaveIDs(),
                getExistingBrAPIData
        );
    }
    @Override
    public ProcessedData process(ImportContext context) {
        ExpUnitMiddlewareContext workflowContext = new ExpUnitMiddlewareContext();
        workflowContext.setImportContext(context);
        this.middleware.process(workflowContext);

        // TODO: implement
        return new ProcessedData();
    }

    @Override
    public String getName() {
        return "AppendOverwritePhenotypesWorkflow";
    }

}
