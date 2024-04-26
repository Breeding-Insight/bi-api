package org.breedinginsight.brapps.importer.services.processors.experiment.pipeline;

public class Pipeline<I, O> {

    private final ProcessingStep<I, O> currentStep;

    public Pipeline(ProcessingStep<I, O> currentStep) {
        this.currentStep = currentStep;
    }

    public <K> Pipeline<I, K> addProcessingStep(ProcessingStep<O, K> newStep) {
        return new Pipeline<>(input -> newStep.process(currentStep.process(input)));
    }

    public O execute(I input) {
        return currentStep.process(input);
    }
}
