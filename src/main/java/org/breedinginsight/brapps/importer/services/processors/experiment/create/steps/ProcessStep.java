package org.breedinginsight.brapps.importer.services.processors.experiment.create.steps;

import org.breedinginsight.brapps.importer.services.processors.experiment.model.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.pipeline.ProcessingStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ExistingData;

public class ProcessStep implements ProcessingStep<ExistingData, ProcessedData> {

    @Override
    public ProcessedData process(ExistingData input) {

        // TODO: implement
        return new ProcessedData();
    }
}
