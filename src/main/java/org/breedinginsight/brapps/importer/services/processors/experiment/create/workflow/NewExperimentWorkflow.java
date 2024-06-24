package org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.imports.ImportServiceContext;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.workflow.ExperimentWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflow;
import org.breedinginsight.brapps.importer.model.workflow.ImportWorkflowResult;
import org.breedinginsight.brapps.importer.services.ImportStatusService;
import org.breedinginsight.brapps.importer.services.processors.ExperimentProcessor;
import org.breedinginsight.brapps.importer.services.processors.ProcessorManager;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Slf4j
@Getter
@Singleton
public class NewExperimentWorkflow implements ExperimentWorkflow {
    private final ExperimentWorkflowNavigator.Workflow workflow;
    private final ImportStatusService statusService;
    private final Provider<ExperimentProcessor> experimentProcessorProvider;
    private final Provider<ProcessorManager> processorManagerProvider;

    @Inject
    public NewExperimentWorkflow(ImportStatusService statusService,
                                 Provider<ExperimentProcessor> experimentProcessorProvider,
                                 Provider<ProcessorManager> processorManagerProvider){
        this.statusService = statusService;
        this.experimentProcessorProvider = experimentProcessorProvider;
        this.processorManagerProvider = processorManagerProvider;
        this.workflow = ExperimentWorkflowNavigator.Workflow.NEW_OBSERVATION;
    }

    @Override
    public Optional<ImportWorkflowResult> process(ImportServiceContext context) {
        // Metadata about this workflow processing the context
        ImportWorkflow workflow = ImportWorkflow.builder()
                .id(getWorkflow().getId())
                .name(getWorkflow().getName())
                .build();

        // No-preview result
        Optional<ImportWorkflowResult> result = Optional.of(ImportWorkflowResult.builder()
                .workflow(workflow)  // attach metadata of this workflow to response
                .importPreviewResponse(Optional.empty())
                .caughtException(Optional.empty())
                .build());

        // Skip this workflow unless creating a new experiment
        if (context != null && !this.workflow.isEqual(context.getWorkflow())) {
            return Optional.empty();
        }

        // Skip processing if no context, but return no-preview result with metadata for this workflow
        if (context == null) {
            return result;
        }

        // Build and return the preview response
        try {
            ImportPreviewResponse successResponse;
            successResponse = processorManagerProvider.get().process(context.getBrAPIImports(),
                    List.of(experimentProcessorProvider.get()),
                    context.getData(),
                    context.getProgram(),
                    context.getUpload(),
                    context.getUser(),
                    context.isCommit());
            result.ifPresent(importWorkflowResult -> importWorkflowResult.setImportPreviewResponse(Optional.of(successResponse)));
        } catch (Exception e) {
            result.ifPresent(importWorkflowResult -> importWorkflowResult.setCaughtException(Optional.of(e)));
        }

        return result;
    }

    @Override
    public int getOrder() {
        return 1;
    }

}
