package org.breedinginsight.brapps.importer.services.processors.experiment;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.workflow.ExperimentWorkflowFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.workflow.Workflow;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Prototype
public class ExperimentProcessor {

    private final ExperimentWorkflowFactory experimentWorkflowFactory;

    @Inject
    public ExperimentProcessor(ExperimentWorkflowFactory experimentWorkflowFactory) {
        this.experimentWorkflowFactory = experimentWorkflowFactory;
    }

    public Map<String, ImportPreviewStatistics> process(ImportContext context)
            throws ApiException, ValidatorException, MissingRequiredInfoException, UnprocessableEntityException {

        // determine which workflow to use based on the import context
        Workflow workflow = experimentWorkflowFactory.getWorkflow(context);
        log.info("Importing experiment data using workflow: " + workflow.getName());

        ProcessedData output = workflow.process(context);


        return new HashMap<>();
    }
}
