package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create;

import io.micronaut.context.ApplicationContext;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;

public class BrAPIObservationUnitCreation extends BrAPICreation<BrAPIObservationUnit> {
    /**
     * Constructor for BrAPICreation class.
     *
     * @param context the ExpUnitMiddlewareContext object
     */
    public BrAPIObservationUnitCreation(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Abstract method to get the ExperimentImportEntity based on the ExpUnitMiddlewareContext.
     *
     * @param context the ExpUnitMiddlewareContext object
     * @return the ExperimentImportEntity object
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
