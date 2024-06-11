package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import io.micronaut.context.ApplicationContext;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;

public class BrAPIObservationUnitReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<BrAPIObservationUnit> {
    /**
     * Constructs a new BrAPIReadWorkflowInitialization object with the given ExpUnitMiddlewareContext.
     * Initializes the entity based on the provided context.
     *
     * @param context the ExpUnitMiddlewareContext used for initialization.
     */
    public BrAPIObservationUnitReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @param context The ExpUnitMiddlewareContext providing information about the entity.
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    @Override
    public ExperimentImportEntity<BrAPIObservationUnit> getEntity(ExpUnitMiddlewareContext context) {
        try (ApplicationContext appContext = ApplicationContext.run()) {
            BrAPIObservationUnitDAO observationUnitDAO = appContext.getBean(BrAPIObservationUnitDAO.class);
            ObservationUnitService observationUnitService = appContext.getBean(ObservationUnitService.class);
            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);

            return new PendingObservationUnit(context, observationUnitDAO, observationUnitService, experimentUtilities);
        }
    }
}
