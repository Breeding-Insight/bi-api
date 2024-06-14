package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.misc;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.BrAPIRead;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

@Slf4j
public class BrAPITrialRead extends BrAPIRead<BrAPITrial> {

    ExpUnitMiddlewareContext context;
    public BrAPITrialRead(ExpUnitMiddlewareContext context) {

        this.context = context;
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
        return null;
    }
}
