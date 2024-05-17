package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;
import org.breedinginsight.model.Program;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class FieldValidation extends ExpUnitMiddleware {
    ObservationUnitService observationUnitService;

    @Inject
    public FieldValidation(ObservationUnitService observationUnitService) {
        this.observationUnitService = observationUnitService;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {

        try {

        } catch (Exception e) {
            this.compensate(context, new MiddlewareError(() -> {
                throw new RuntimeException(e);
            }));
        }

        return processNext(context);
    }
}
