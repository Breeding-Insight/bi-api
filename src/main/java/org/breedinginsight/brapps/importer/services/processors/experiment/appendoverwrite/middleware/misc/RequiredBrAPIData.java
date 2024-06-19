package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.misc;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;

@Slf4j
public class RequiredBrAPIData extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;

    @Inject
    public RequiredBrAPIData(RequiredObservationUnits requiredObservationUnits,
                             RequiredTrials requiredTrials,
                             RequiredStudies requiredStudies,
                             RequiredLocations requiredLocations,
                             RequiredDatasets requiredDatasets,
                             RequiredGermplasm requiredGermplasm) {

        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(
                requiredObservationUnits, // Fetch the BrAPI units for the required exp unit ids
                requiredTrials,           // Fetch the BrAPI trials belonging to the exp units
                requiredStudies,          // Fetch the BrAPI studies belonging to the exp units
                requiredLocations,        // Fetch the BrAPI locations belonging to the exp units
                requiredDatasets,         // Fetch the dataset belonging to the exp units
                requiredGermplasm);       // Fetch the germplasm belonging to the exp units
    }

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {
        log.debug("reading required BrAPI data from BrAPI service");
        return this.middleware.process(context);
    }
}
