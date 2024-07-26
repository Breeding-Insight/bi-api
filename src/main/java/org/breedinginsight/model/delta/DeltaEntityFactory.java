package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;

import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;

import javax.inject.Inject;

@Factory
public class DeltaEntityFactory {

    private final ObservationUnitService observationUnitService;

    @Inject
    public DeltaEntityFactory(ObservationUnitService observationUnitService) {
        this.observationUnitService = observationUnitService;
    }

    public static Experiment makeExperiment(BrAPITrial brAPIObject) {
        return new Experiment(brAPIObject);
    }

    public static Environment makeEnvironment(BrAPIStudy brAPIObject) {
        return new Environment(brAPIObject);
    }

    public static DeltaGermplasm makeDeltaGermplasm(BrAPIGermplasm brAPIObject) {
        return new DeltaGermplasm(brAPIObject);
    }

    public static DeltaLocation makeDeltaLocation(BrAPILocation brAPIObject) {
        return new DeltaLocation(brAPIObject);
    }

    public static DeltaObservation makeDeltaObservation(BrAPIObservation brAPIObject) {
        return new DeltaObservation(brAPIObject);
    }

    public static DeltaObservationUnit makeDeltaObservationUnit(BrAPIObservationUnit brAPIObject, ObservationUnitService observationUnitService) {
        return new DeltaObservationUnit(brAPIObject, observationUnitService);
    }

    public static DeltaObservationVariable makeDeltaObservationVariable(BrAPIObservationVariable brAPIObject) {
        return new DeltaObservationVariable(brAPIObject);
    }

    @Bean
    @Prototype
    public Experiment makeExperimentBean(BrAPITrial brAPIObject) {
        return makeExperiment(brAPIObject);
    }

    @Bean
    @Prototype
    public Environment makeEnvironmentBean(BrAPIStudy brAPIObject) {
        return makeEnvironment(brAPIObject);
    }

    @Bean
    @Prototype
    public DeltaGermplasm makeDeltaGermplasmBean(BrAPIGermplasm brAPIObject) {
        return makeDeltaGermplasm(brAPIObject);
    }

    @Bean
    @Prototype
    public DeltaLocation makeDeltaLocationBean(BrAPILocation brAPIObject) {
        return makeDeltaLocation(brAPIObject);
    }

    @Bean
    @Prototype
    public DeltaObservation makeDeltaObservationBean(BrAPIObservation brAPIObject) {
        return makeDeltaObservation(brAPIObject);
    }

    @Bean
    @Prototype
    public DeltaObservationUnit makeDeltaObservationUnitBean(BrAPIObservationUnit brAPIObject) {
        return makeDeltaObservationUnit(brAPIObject, observationUnitService);
    }

    @Bean
    @Prototype
    public DeltaObservationVariable makeDeltaObservationVariableBean(BrAPIObservationVariable brAPIObject) {
        return makeDeltaObservationVariable(brAPIObject);
    }
}
