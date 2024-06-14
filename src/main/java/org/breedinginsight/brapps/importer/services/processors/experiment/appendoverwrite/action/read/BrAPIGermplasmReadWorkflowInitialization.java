package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import io.micronaut.context.ApplicationContext;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.misc.BrAPIReadWorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingGermplasm;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.GermplasmService;

public class BrAPIGermplasmReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<BrAPIGermplasm> {
    ExpUnitMiddlewareContext context;
    /**
     * Constructs a new BrAPIReadWorkflowInitialization object with the given ExpUnitMiddlewareContext.
     * Initializes the entity based on the provided context.
     *
     * @param context the ExpUnitMiddlewareContext used for initialization.
     */
    public BrAPIGermplasmReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        this.context = context;
    }

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    @Override
    public ExperimentImportEntity<BrAPIGermplasm> getEntity() {
        try (ApplicationContext appContext = ApplicationContext.run()) {
            GermplasmService germplasmService = appContext.getBean(GermplasmService.class);
            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);

            return new PendingGermplasm(context, germplasmService, experimentUtilities);
        }
    }
}
