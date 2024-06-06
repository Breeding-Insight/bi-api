package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.brapi;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.MiddlewareError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Provider;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.ErrMessage.MULTIPLE_EXP_TITLES;

@Slf4j
public class NewPendingBrAPIObjects extends ExpUnitMiddleware {
    ExpUnitMiddleware middleware;
    Provider<PendingObservation> pendingObservationProvider;
    @Inject
    public NewPendingBrAPIObjects(Provider<PendingObservation> pendingObservationProvider) {
        this.middleware = (ExpUnitMiddleware) ExpUnitMiddleware.link(pendingObservationProvider.get()); // Construct new pending observation
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        log.debug("constructing new pending BrAPI objects");


        return processNext(context);
    }
}
