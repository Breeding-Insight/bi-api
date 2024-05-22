package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import java.util.List;
import java.util.Optional;

public class UnchangedData extends VisitedObservationData {
    BrAPIObservation observation;
    Program program;

    public UnchangedData(BrAPIObservation observation, Program program) {
        this.observation = observation;
        this.program = program;
    }
    @Override
    public Optional<List<ValidationError>> getValidationErrors() {
        return Optional.empty();
    }

    @Override
    public PendingImportObject<BrAPIObservation> constructPendingObservation() {
        // Construct a pending observation with a status set to EXISTING
        PendingImportObject<BrAPIObservation> pendingExistingObservation = new PendingImportObject<>(ImportObjectState.EXISTING, (BrAPIObservation) Utilities.formatBrapiObjForDisplay(observation, BrAPIObservation.class, program));

        return pendingExistingObservation;
    }

    @Override
    public void updateTally(AppendStatistic statistic) {
        statistic.incrementExistingCount(1);
    }
}
