package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.misc;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
public class BrAPITrialReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<BrAPITrial> {

    ExpUnitMiddlewareContext context;

    public BrAPITrialReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        this.context = context;
    }

    @Override
    public ExperimentImportEntity<BrAPITrial> getEntity() {

        return pendingEntityFactory.pendingTrialBean(context);
//        try (ApplicationContext appContext = ApplicationContext.run()) {
//            TrialService trialService = appContext.getBean(TrialService.class);
//            BrAPITrialDAO brAPITrialDAO = appContext.getBean(BrAPITrialDAO.class);
//            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);
//
//            return new PendingTrial(context, trialService, brAPITrialDAO, experimentUtilities);
//        }
    }
}
