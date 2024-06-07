package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;

import javax.inject.Inject;

@Slf4j
public class FieldValidation extends ExpUnitMiddleware {
    ObservationUnitService observationUnitService;

    @Inject
    public FieldValidation(ObservationUnitService observationUnitService) {
        this.observationUnitService = observationUnitService;
    }

    @Override
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {

        try {

        } catch (Exception e) {
            this.compensate(context);
        }

        return processNext(context);
    }
}
