/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow;

import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.model.workflow.ProcessedData;
import org.breedinginsight.brapps.importer.model.workflow.Workflow;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.steps.GetExistingProcessingStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.steps.ProcessStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.pipeline.Pipeline;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

/**
 * This class represents a workflow for creating a new experiment. The bean name must match the appropriate bean column
 * value in the import_mapping_workflow db table
 */
@Prototype
@Named("CreateNewExperimentWorkflow")
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
        // TODO
        Pipeline<ImportContext, ProcessedData> pipeline = new Pipeline<>(getExistingStepProvider.get())
                .addProcessingStep(processStepProvider.get());
        ProcessedData processed = pipeline.execute(context);

        // TODO: return actual data
        return processed;
    }

    /**
     * Retrieves the name of the workflow. This is used for logging display purposes.
     *
     * @return the name of the workflow
     */
    @Override
    public String getName() {
        return "CreateNewExperimentWorkflow";
    }
}
