package org.breedinginsight.model.delta;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import lombok.NonNull;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;

import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationVariable;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;

@Factory
public class DeltaEntityFactory {

    private final ObservationUnitService observationUnitService;
    private final BrAPIGermplasmService germplasmService;
    private final String applicationReferenceSourceBase;

    @Inject
    public DeltaEntityFactory(ObservationUnitService observationUnitService,
                              BrAPIGermplasmService germplasmService,
                              @Property(name = "brapi.server.reference-source") String applicationReferenceSourceBase) {
        this.observationUnitService = observationUnitService;
        this.germplasmService = germplasmService;
        this.applicationReferenceSourceBase = applicationReferenceSourceBase;
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

    private DeltaGermplasmListSummary makeDeltaGermplasmListSummary(BrAPIListSummary brAPIObject) {
        return new DeltaGermplasmListSummary(brAPIObject);
    }

    private DeltaGermplasmListDetails makeDeltaGermplasmListDetails(BrAPIListDetails brAPIObject, String referenceSourceBase, BrAPIGermplasmService germplasmService) {
        return new DeltaGermplasmListDetails(brAPIObject, referenceSourceBase, germplasmService);
    }

    @Bean
    @Prototype
    public DeltaListSummary makeDeltaListSummaryBean(@NonNull BrAPIListSummary brAPIObject) {
        BrAPIListTypes listType = brAPIObject.getListType();
        switch (listType) {
            case GERMPLASM: return makeDeltaGermplasmListSummary(brAPIObject);
            default: return null;
        }
    }

    @Bean
    @Prototype
    public DeltaListDetails makeDeltaListDetailsBean(@NonNull BrAPIListDetails brAPIObject) {
        BrAPIListTypes listType = brAPIObject.getListType();
        switch (listType) {
            case GERMPLASM: return makeDeltaGermplasmListDetailsBean(brAPIObject);
            default: return null;
        }
    }

    @Bean
    @Prototype
    public DeltaGermplasmListSummary makeDeltaGermplasmListSummaryBean(@NonNull BrAPIListSummary brAPIObject) {
        return makeDeltaGermplasmListSummary(brAPIObject);
    }

    @Bean
    @Prototype
    public DeltaGermplasmListDetails makeDeltaGermplasmListDetailsBean(@NonNull BrAPIListDetails brAPIObject) {
        return makeDeltaGermplasmListDetails(brAPIObject, applicationReferenceSourceBase, germplasmService);
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
