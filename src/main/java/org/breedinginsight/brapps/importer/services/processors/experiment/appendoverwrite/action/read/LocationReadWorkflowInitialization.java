package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read.misc.BrAPIReadWorkflowInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingLocation;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.LocationService;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.services.ProgramLocationService;

@Slf4j
public class LocationReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<ProgramLocation> {
    ExpUnitMiddlewareContext context;
    /**
     * Constructs a new BrAPIReadWorkflowInitialization object with the given ExpUnitMiddlewareContext.
     * Initializes the entity based on the provided context.
     *
     * @param context the ExpUnitMiddlewareContext used for initialization.
     */
    public LocationReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        this.context = context;
    }

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    @Override
    public ExperimentImportEntity<ProgramLocation> getEntity() {
        try (ApplicationContext appContext = ApplicationContext.run()) {
            ProgramLocationService programLocationService = appContext.getBean(ProgramLocationService.class);
            LocationService locationService = appContext.getBean(LocationService.class);
            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);

            return new PendingLocation(context, programLocationService, locationService, experimentUtilities);
        }
    }
}
