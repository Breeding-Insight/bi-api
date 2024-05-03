package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.services.exceptions.ValidatorException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class StatisticsService {
    // TODO: used by both workflows
    public Map<String, ImportPreviewStatistics> generateStatisticsMap(List<BrAPIImport> importRows) {
        // Data for stats.
        HashSet<String> environmentNameCounter = new HashSet<>(); // set of unique environment names
        HashSet<String> obsUnitsIDCounter = new HashSet<>(); // set of unique observation unit ID's
        HashSet<String> gidCounter = new HashSet<>(); // set of unique GID's

        for (BrAPIImport row : importRows) {
            ExperimentObservation importRow = (ExperimentObservation) row;
            // Collect date for stats.
            addIfNotNull(environmentNameCounter, importRow.getEnv());
            addIfNotNull(obsUnitsIDCounter, createObservationUnitKey(importRow));
            addIfNotNull(gidCounter, importRow.getGid());
        }

        int numNewObservations = Math.toIntExact(
                observationByHash.values()
                        .stream()
                        .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW &&
                                !StringUtils.isBlank(preview.getBrAPIObject()
                                        .getValue()))
                        .count()
        );

        int numExistingObservations = Math.toIntExact(
                this.observationByHash.values()
                        .stream()
                        .filter(preview -> preview != null && preview.getState() == ImportObjectState.EXISTING &&
                                !StringUtils.isBlank(preview.getBrAPIObject()
                                        .getValue()))
                        .count()
        );

        int numMutatedObservations = Math.toIntExact(
                this.observationByHash.values()
                        .stream()
                        .filter(preview -> preview != null && preview.getState() == ImportObjectState.MUTATED &&
                                !StringUtils.isBlank(preview.getBrAPIObject()
                                        .getValue()))
                        .count()
        );


        ImportPreviewStatistics environmentStats = ImportPreviewStatistics.builder()
                .newObjectCount(environmentNameCounter.size())
                .build();
        ImportPreviewStatistics obdUnitStats = ImportPreviewStatistics.builder()
                .newObjectCount(obsUnitsIDCounter.size())
                .build();
        ImportPreviewStatistics gidStats = ImportPreviewStatistics.builder()
                .newObjectCount(gidCounter.size())
                .build();
        ImportPreviewStatistics observationStats = ImportPreviewStatistics.builder()
                .newObjectCount(numNewObservations)
                .build();
        ImportPreviewStatistics existingObservationStats = ImportPreviewStatistics.builder()
                .newObjectCount(numExistingObservations)
                .build();
        ImportPreviewStatistics mutatedObservationStats = ImportPreviewStatistics.builder()
                .newObjectCount(numMutatedObservations)
                .build();

        return Map.of(
                "Environments", environmentStats,
                "Observation_Units", obdUnitStats,
                "GIDs", gidStats,
                "Observations", observationStats,
                "Existing_Observations", existingObservationStats,
                "Mutated_Observations", mutatedObservationStats
        );
    }

    // TODO: used by both workflows
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // TODO
    }
}
