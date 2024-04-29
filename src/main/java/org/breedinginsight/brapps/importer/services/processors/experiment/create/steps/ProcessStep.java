package org.breedinginsight.brapps.importer.services.processors.experiment.create.steps;

import org.breedinginsight.brapps.importer.services.processors.experiment.model.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.pipeline.ProcessingStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;

public class ProcessStep implements ProcessingStep<PendingData, ProcessedData> {

    @Override
    public ProcessedData process(PendingData input) {

        // TODO: implement
        return new ProcessedData();
    }
}
