package org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.steps.GetExistingProcessingStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.steps.ProcessStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.pipeline.Pipeline;
import org.breedinginsight.brapps.importer.services.processors.experiment.workflow.Workflow;

import javax.inject.Inject;
import javax.inject.Provider;

@Prototype
@Slf4j
public class CreateNewExperimentWorkflow implements Workflow {

    private final Provider<GetExistingProcessingStep> getExistingStepProvider;
    private final Provider<ProcessStep> processStepProvider;

    @Inject
    public CreateNewExperimentWorkflow(Provider<GetExistingProcessingStep> getExistingStepProvider,
                                       Provider<ProcessStep> processStepProvider) {
        this.getExistingStepProvider = getExistingStepProvider;
        this.processStepProvider = processStepProvider;
    }

    @Override
    public ProcessedData process(ImportContext context) {

        Pipeline<ImportContext, ProcessedData> pipeline = new Pipeline<>(getExistingStepProvider.get())
                .addProcessingStep(processStepProvider.get());
        ProcessedData processed = pipeline.execute(context);

        // TODO: return actual data
        return processed;
    }

    @Override
    public String getName() {
        return "CreateNewExperimentWorkflow";
    }
}