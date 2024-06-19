package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.misc;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingDataset;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.DatasetService;

@Slf4j
public class BrAPIDatasetCreation extends BrAPICreation<BrAPIListDetails> {
    ExpUnitMiddlewareContext context;
    /**
     * Constructor for BrAPICreation class.
     *
     * @param context the ExpUnitMiddlewareContext object
     */
    public BrAPIDatasetCreation(ExpUnitMiddlewareContext context) {

        this.context = context;
    }

    /**
     * Abstract method to get the ExperimentImportEntity based on the ExpUnitMiddlewareContext.
     *
     * @return the ExperimentImportEntity object
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
