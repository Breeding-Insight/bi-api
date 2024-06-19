package org.breedinginsight.brapps.importer.services.processors.experiment.pipeline;

public interface ProcessingStep<I, O> {
    O process(I input);
}
