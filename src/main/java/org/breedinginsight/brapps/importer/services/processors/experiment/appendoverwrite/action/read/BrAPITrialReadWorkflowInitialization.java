package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.BrAPIReadWorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingTrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.TrialService;

@Slf4j
public class BrAPITrialReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<BrAPITrial> {

    public BrAPITrialReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        super(context);
    }

    @Override
    public ExperimentImportEntity<BrAPITrial> getEntity(ExpUnitMiddlewareContext context) {
        try (ApplicationContext appContext = ApplicationContext.run()) {
            TrialService trialService = appContext.getBean(TrialService.class);
            BrAPITrialDAO brAPITrialDAO = appContext.getBean(BrAPITrialDAO.class);
            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);

            return new PendingTrial(context, trialService, brAPITrialDAO, experimentUtilities);
        }
    }
}
