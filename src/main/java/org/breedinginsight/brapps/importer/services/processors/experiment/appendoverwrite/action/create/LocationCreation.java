package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create;

import io.micronaut.context.ApplicationContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingLocation;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.LocationService;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.services.ProgramLocationService;

public class LocationCreation extends BrAPICreation<ProgramLocation>{
    /**
     * Constructor for BrAPICreation class.
     *
     * @param context the ExpUnitMiddlewareContext object
     */
    public LocationCreation(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Abstract method to get the ExperimentImportEntity based on the ExpUnitMiddlewareContext.
     *
     * @param context the ExpUnitMiddlewareContext object
     * @return the ExperimentImportEntity object
     */
    @Override
    public ExperimentImportEntity<ProgramLocation> getEntity(ExpUnitMiddlewareContext context) {
        try (ApplicationContext appContext = ApplicationContext.run()) {
            ProgramLocationService programLocationService = appContext.getBean(ProgramLocationService.class);
            LocationService locationService = appContext.getBean(LocationService.class);
            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);

            return new PendingLocation(context, programLocationService, locationService, experimentUtilities);
        }
    }
}
