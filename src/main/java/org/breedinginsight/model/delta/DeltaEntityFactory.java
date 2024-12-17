package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import lombok.NonNull;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;
import org.breedinginsight.model.ProgramLocation;

import javax.inject.Inject;

@Factory
public class DeltaEntityFactory {

    private final ObservationUnitService observationUnitService;

    @Inject
    public DeltaEntityFactory(ObservationUnitService observationUnitService) {
        this.observationUnitService = observationUnitService;
    }

    private Experiment makeExperiment(BrAPITrial brAPIObject) {
        return new Experiment(brAPIObject);
    }

    private Environment makeEnvironment(BrAPIStudy brAPIObject) {
        return new Environment(brAPIObject);
    }

    private DeltaGermplasm makeDeltaGermplasm(BrAPIGermplasm brAPIObject) {
        return new DeltaGermplasm(brAPIObject);
    }

    private DeltaLocation makeDeltaLocation(ProgramLocation entity) {
        return new DeltaLocation(entity);
    }

    private DeltaObservation makeDeltaObservation(BrAPIObservation brAPIObject) {
        return new DeltaObservation(brAPIObject);
    }

    private DeltaObservationUnit makeDeltaObservationUnit(BrAPIObservationUnit brAPIObject, ObservationUnitService observationUnitService) {
        return new DeltaObservationUnit(brAPIObject, observationUnitService);
    }

    private DeltaObservationVariable makeDeltaObservationVariable(BrAPIObservationVariable brAPIObject) {
        return new DeltaObservationVariable(brAPIObject);
    }

    @Bean
    @Prototype
    public Experiment makeExperimentBean(@NonNull BrAPITrial brAPIObject) {
        return makeExperiment(brAPIObject);
    }

    @Bean
    @Prototype
    public Environment makeEnvironmentBean(@NonNull BrAPIStudy brAPIObject) {
        return makeEnvironment(brAPIObject);
    }

    @Bean
    @Prototype
    public DeltaGermplasm makeDeltaGermplasmBean(@NonNull BrAPIGermplasm brAPIObject) {
        return makeDeltaGermplasm(brAPIObject);
    }

    @Bean
    @Prototype
    public DeltaLocation makeDeltaLocationBean(@NonNull ProgramLocation entity) {
        return makeDeltaLocation(entity);
    }

    @Bean
    @Prototype
    public DeltaObservation makeDeltaObservationBean(@NonNull BrAPIObservation brAPIObject) {
        return makeDeltaObservation(brAPIObject);
    }

    @Bean
    @Prototype
    public DeltaObservationUnit makeDeltaObservationUnitBean(@NonNull BrAPIObservationUnit brAPIObject) {
        return makeDeltaObservationUnit(brAPIObject, observationUnitService);
    }

    @Bean
    @Prototype
    public DeltaObservationVariable makeDeltaObservationVariableBean(@NonNull BrAPIObservationVariable brAPIObject) {
        return makeDeltaObservationVariable(brAPIObject);
    }
}
