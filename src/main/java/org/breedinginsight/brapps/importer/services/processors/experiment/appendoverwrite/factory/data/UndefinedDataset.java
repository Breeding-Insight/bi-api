package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.data;

import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.AppendStatistic;

import java.util.List;
import java.util.Optional;

public class UndefinedDataset extends VisitedObservationData {
    @Override
    public Optional<List<ValidationError>> getValidationErrors() {
        return Optional.empty();
    }

    @Override
    public PendingImportObject<BrAPIObservation> constructPendingObservation() {
        return null;
    }

    @Override
    public void updateTally(AppendStatistic statistic) {

    }
}
