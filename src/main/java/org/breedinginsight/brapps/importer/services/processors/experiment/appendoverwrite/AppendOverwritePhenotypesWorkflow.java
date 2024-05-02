package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite;

import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.GetExistingBrAPIData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ValidateAllRowsHaveIDs;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.workflow.Workflow;

import javax.inject.Inject;
import javax.inject.Provider;


@Prototype
public class AppendOverwritePhenotypesWorkflow implements Workflow {

    ExpUnitMiddleware middleware;
    Provider<GetExistingBrAPIData> getExistingBrAPIDataProvider;
    @Inject
    public AppendOverwritePhenotypesWorkflow(Provider<GetExistingBrAPIData> getExistingBrAPIDataProvider) {
        this.middleware.link(
                new ValidateAllRowsHaveIDs(),
                getExistingBrAPIDataProvider.get()
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
