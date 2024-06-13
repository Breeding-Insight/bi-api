package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import io.micronaut.context.ApplicationContext;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingDataset;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.DatasetService;

public class BrAPIDatasetReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<BrAPIListDetails>{
    /**
     * Constructs a new BrAPIReadWorkflowInitialization object with the given ExpUnitMiddlewareContext.
     * Initializes the entity based on the provided context.
     *
     * @param context the ExpUnitMiddlewareContext used for initialization.
     */
    ExpUnitMiddlewareContext context;
    public BrAPIDatasetReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        this.context = context;
    }

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    @Override
    public ExperimentImportEntity<BrAPIListDetails> getEntity() {
        try (ApplicationContext appContext = ApplicationContext.run()) {
            BrAPIListDAO brAPIListDAO = appContext.getBean(BrAPIListDAO.class);
            DatasetService datasetService = appContext.getBean(DatasetService.class);
            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);

            return new PendingDataset(context, brAPIListDAO, datasetService, experimentUtilities);
        }
    }
}
