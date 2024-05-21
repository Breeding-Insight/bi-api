package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;

import java.util.List;
import java.util.Optional;

public abstract class VisitedObservationData {
    abstract public Optional<List<ValidationError>> getValidationErrors();
    abstract public PendingImportObject<BrAPIObservation> constructPendingObservation();
}
