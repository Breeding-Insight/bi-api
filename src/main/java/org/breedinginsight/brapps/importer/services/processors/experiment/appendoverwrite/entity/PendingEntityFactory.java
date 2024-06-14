package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapi.v2.dao.*;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.*;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.ProgramLocationService;

import javax.inject.Inject;

@Factory
public class PendingEntityFactory {
    private final TrialService trialService;
    private final BrAPITrialDAO brapiTrialDAO;
    private final BrAPIObservationUnitDAO observationUnitDAO;
    private final ObservationUnitService observationUnitService;
    private final StudyService studyService;
    private final BrAPIStudyDAO brAPIStudyDAO;
    private final GermplasmService germplasmService;
    private final BrAPIListDAO brAPIListDAO;
    private final DatasetService datasetService;
    private final BrAPIObservationDAO brAPIObservationDAO;
    private final OntologyService ontologyService;
    private final ProgramLocationService programLocationService;
    private final LocationService locationService;
    private final ExperimentUtilities experimentUtilities;

    @Inject
    public PendingEntityFactory(TrialService trialService,
                                BrAPITrialDAO brapiTrialDAO,
                                BrAPIObservationUnitDAO observationUnitDAO,
                                ObservationUnitService observationUnitService,
                                StudyService studyService,
                                BrAPIStudyDAO brAPIStudyDAO,
                                GermplasmService germplasmService,
                                BrAPIListDAO brAPIListDAO,
                                DatasetService datasetService,
                                BrAPIObservationDAO brAPIObservationDAO,
                                OntologyService ontologyService, ProgramLocationService programLocationService, LocationService locationService,
                                ExperimentUtilities experimentUtilities) {
        this.trialService = trialService;
        this.brapiTrialDAO = brapiTrialDAO;
        this.observationUnitDAO = observationUnitDAO;
        this.observationUnitService = observationUnitService;
        this.studyService = studyService;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.germplasmService = germplasmService;
        this.brAPIListDAO = brAPIListDAO;
        this.datasetService = datasetService;
        this.brAPIObservationDAO = brAPIObservationDAO;
        this.ontologyService = ontologyService;
        this.programLocationService = programLocationService;
        this.locationService = locationService;
        this.experimentUtilities = experimentUtilities;
    }

    public static PendingTrial pendingTrial(ExpUnitMiddlewareContext context,
                                            TrialService trialService,
                                            BrAPITrialDAO brapiTrialDAO,
                                            ExperimentUtilities experimentUtilities) {
        return new PendingTrial(context, trialService, brapiTrialDAO, experimentUtilities);
    }

    public static PendingObservationUnit pendingObservationUnit(ExpUnitMiddlewareContext context,
                                            BrAPIObservationUnitDAO observationUnitDAO,
                                            ObservationUnitService observationUnitService,
                                            ExperimentUtilities experimentUtilities) {
        return new PendingObservationUnit(context, observationUnitDAO, observationUnitService, experimentUtilities);
    }

    public static PendingStudy pendingStudy(ExpUnitMiddlewareContext context,
                                            StudyService studyService,
                                            BrAPIStudyDAO brAPIStudyDAO,
                                            ExperimentUtilities experimentUtilities) {
        return new PendingStudy(context, studyService, brAPIStudyDAO, experimentUtilities);
    }

    public static PendingGermplasm pendingGermplasm(ExpUnitMiddlewareContext context,
                                                    GermplasmService germplasmService,
                                                    ExperimentUtilities experimentUtilities) {
        return new PendingGermplasm(context, germplasmService, experimentUtilities);
    }

    public static PendingDataset pendingDataset(ExpUnitMiddlewareContext context,
                                                BrAPIListDAO brAPIListDAO,
                                                DatasetService datasetService,
                                                ExperimentUtilities experimentUtilities) {
        return new PendingDataset(context, brAPIListDAO, datasetService, experimentUtilities);
    }

    public static PendingObservation pendingObservation(ExpUnitMiddlewareContext context,
                                                        BrAPIObservationDAO brAPIObservationDAO,
                                                        OntologyService ontologyService,
                                                        ExperimentUtilities experimentUtilities) {
        return new PendingObservation(context, brAPIObservationDAO, ontologyService, experimentUtilities);
    }

    public static PendingLocation pendingLocation(ExpUnitMiddlewareContext context,
                                                  ProgramLocationService programLocationService,
                                                  LocationService locationService,
                                                  ExperimentUtilities experimentUtilities) {
        return new PendingLocation(context, programLocationService, locationService, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingTrial pendingTrialBean(ExpUnitMiddlewareContext context) {
        return pendingTrial(context, trialService, brapiTrialDAO, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingObservationUnit pendingObservationUnitBean(ExpUnitMiddlewareContext context) {
        return pendingObservationUnit(context, observationUnitDAO, observationUnitService, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingStudy pendingStudyBean(ExpUnitMiddlewareContext context) {
        return pendingStudy(context, studyService, brAPIStudyDAO, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingGermplasm pendingGermplasmBean(ExpUnitMiddlewareContext context) {
        return pendingGermplasm(context, germplasmService, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingDataset pendingDataset(ExpUnitMiddlewareContext context) {
        return pendingDataset(context, brAPIListDAO, datasetService, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingObservation pendingObservation(ExpUnitMiddlewareContext context) {
        return pendingObservation(context, brAPIObservationDAO, ontologyService, experimentUtilities);
    }

    @Bean
    @Prototype
    public PendingLocation pendingLocation(ExpUnitMiddlewareContext context) {
        return pendingLocation(context, programLocationService, locationService, experimentUtilities);
    }
}
