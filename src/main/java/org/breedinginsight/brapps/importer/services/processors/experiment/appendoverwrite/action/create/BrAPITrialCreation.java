package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
public class BrAPITrialCreation extends BrAPICreation<BrAPITrial> {
    ExpUnitMiddlewareContext context;
    private PendingEntityFactory pendingEntityFactory;
    public BrAPITrialCreation(ExpUnitMiddlewareContext context,
                              PendingEntityFactory pendingEntityFactory) {

        this.context = context;
        this.pendingEntityFactory = pendingEntityFactory;
    }

    @Override
    public ExperimentImportEntity<BrAPITrial> getEntity() {
//        try (ApplicationContext appContext = ApplicationContext.run()) {
//            TrialService trialService = appContext.getBean(TrialService.class);
//            BrAPITrialDAO brAPITrialDAO = appContext.getBean(BrAPITrialDAO.class);
//            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);
//
//            return new PendingTrial(context, trialService, brAPITrialDAO, experimentUtilities);
//        }
        return pendingEntityFactory.pendingTrialBean(context);
    }
}
