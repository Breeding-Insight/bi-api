package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.initialize;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.*;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.misc.BrAPIStudyReadWorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.misc.BrAPIDatasetReadWorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.misc.BrAPIGermplasmReadWorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.model.ProgramLocation;

import javax.inject.Inject;

@Slf4j
@Prototype
public class WorkflowInitialization extends ExpUnitMiddleware {
    WorkflowReadInitialization<BrAPIObservationUnit> brAPIObservationUnitReadWorkflowInitialization;
    WorkflowReadInitialization<BrAPITrial> brAPITrialReadWorkflowInitialization;
    WorkflowReadInitialization<BrAPIStudy> brAPIStudyReadWorkflowInitialization;
    WorkflowReadInitialization<ProgramLocation> locationReadWorkflowInitialization;
    WorkflowReadInitialization<BrAPIListDetails> brAPIDatasetReadWorkflowInitialization;
    WorkflowReadInitialization<BrAPIGermplasm> brAPIGermplasmReadWorkflowInitialization;
    PendingEntityFactory pendingEntityFactory;
    BrAPIReadFactory brAPIReadFactory;

    @Inject
    public WorkflowInitialization(PendingEntityFactory pendingEntityFactory, BrAPIReadFactory brAPIReadFactory) {
        this.pendingEntityFactory = pendingEntityFactory;
        this.brAPIReadFactory = brAPIReadFactory;
    }
    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        brAPIObservationUnitReadWorkflowInitialization = brAPIReadFactory.observationUnitWorkflowReadInitializationBean(context);
        brAPITrialReadWorkflowInitialization = brAPIReadFactory.trialWorkflowReadInitializationBean(context);
        brAPIStudyReadWorkflowInitialization = brAPIReadFactory.studyWorkflowReadInitializationBean(context);
        locationReadWorkflowInitialization = brAPIReadFactory.locationWorkflowReadInitializationBean(context);
        brAPIDatasetReadWorkflowInitialization = brAPIReadFactory.datasetWorkflowReadInitializationBean(context);
        brAPIGermplasmReadWorkflowInitialization = brAPIReadFactory.germplasmWorkflowReadInitializationBean(context);

        log.debug("reading required BrAPI data from BrAPI service");
        try {
            brAPIObservationUnitReadWorkflowInitialization.execute();
            brAPITrialReadWorkflowInitialization.execute();
            brAPIStudyReadWorkflowInitialization.execute();
            locationReadWorkflowInitialization.execute();
            brAPIDatasetReadWorkflowInitialization.execute();
            brAPIGermplasmReadWorkflowInitialization.execute();
        } catch (ApiException e) {
            context.getExpUnitContext().setProcessError(new MiddlewareError(e));
            return this.compensate(context);
        }

        return processNext(context);
    }
}
