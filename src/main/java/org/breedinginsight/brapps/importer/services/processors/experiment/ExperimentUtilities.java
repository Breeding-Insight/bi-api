package org.breedinginsight.brapps.importer.services.processors.experiment;

import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;

import java.util.List;
import java.util.stream.Collectors;

public class ExperimentUtilities {

    public static final CharSequence COMMA_DELIMITER = ",";

    public static List<ExperimentObservation> importRowsToExperimentObservations(List<BrAPIImport> importRows) {
        return importRows.stream()
                .map(trialImport -> (ExperimentObservation) trialImport)
                .collect(Collectors.toList());
    }


}
