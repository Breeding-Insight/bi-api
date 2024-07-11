package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.model.delta.DeltaGermplasm;
import org.breedinginsight.model.delta.DeltaObservation;
import org.breedinginsight.model.delta.DeltaObservationUnit;
import org.breedinginsight.model.delta.DeltaObservationVariable;
import org.breedinginsight.model.delta.Environment;

import org.apache.commons.lang3.NotImplementedException;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;

@Factory
public class DeltaEntityFactory {
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
        throw new NotImplementedException();
    }

    public static DeltaObservation makeDeltaObservation(BrAPIObservation brAPIObject) {
        throw new NotImplementedException();
    }

    public static DeltaObservationUnit makeDeltaObservationUnit(BrAPIObservationUnit brAPIObject) {
        throw new NotImplementedException();
    }

    public static DeltaObservationVariable makeDeltaObservationVariable(BrAPIObservationVariable brAPIObject) {
        throw new NotImplementedException();
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
        return makeDeltaObservationUnit(brAPIObject);
    }

    @Bean
    @Prototype
    public DeltaObservationVariable makeDeltaObservationVariableBean(BrAPIObservationVariable brAPIObject) {
        return makeDeltaObservationVariable(brAPIObject);
    }
}
