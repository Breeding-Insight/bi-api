package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.read.brapi;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import javax.inject.Inject;
import javax.inject.Provider;

@Slf4j
public class RequiredBrAPIData extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;
    Provider<RequiredObservationUnits> requiredObservationUnitsProvider;
    Provider<RequiredTrials> requiredTrialsProvider;
    Provider<RequiredStudies> requiredStudiesProvider;

    @Inject
    public RequiredBrAPIData(Provider<RequiredObservationUnits> requiredObservationUnitsProvider,
                             Provider<RequiredTrials> requiredTrialsProvider,
                             Provider<RequiredStudies> requiredStudiesProvider) {
        this.middleware.link(requiredObservationUnitsProvider.get(), // Fetch the BrAPI units for the required exp unit ids
                requiredTrialsProvider.get(),   // Fetch the BrAPI trials belonging to the exp units
                requiredStudiesProvider.get()); // Fetch the BrAPI studies belonging to the exp units
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        log.debug("reading required BrAPI data from BrAPI service");
        return this.middleware.process(context);
    }
}
