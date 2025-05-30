package org.breedinginsight.utilities;

import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.model.workflow.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Singleton
public class ImportUtils {

    public ImportPreviewResponse buildImportPreviewResponse(List<BrAPIImport> importRows, PendingData pendingData, ProcessedData processedData,
                                                             ImportUpload upload) {

        Map<Integer, PendingImport> mappedBrAPIImport = processedData.getMappedBrAPIImport();
        Map<String, ImportPreviewStatistics> statistics = generateStatisticsMap(pendingData, importRows);

        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setStatistics(statistics);
        List<PendingImport> mappedBrAPIImportList = new ArrayList<>(mappedBrAPIImport.values());
        response.setRows(mappedBrAPIImportList);
        response.setDynamicColumnNames(upload.getDynamicColumnNamesList());
        return response;
    }

    public long getNewObjectCount(ImportPreviewResponse response) {
        // get total number of new brapi objects to create
        long totalObjects = 0;
        for (ImportPreviewStatistics stats : response.getStatistics().values()) {
            totalObjects += stats.getNewObjectCount();
        }
        return totalObjects;
    }

    public Map<String, ImportPreviewStatistics> generateStatisticsMap(PendingData pendingData, List<BrAPIImport> importRows) {
        // Data for stats.
        HashSet<String> environmentNameCounter = new HashSet<>(); // set of unique environment names
        HashSet<String> obsUnitsIDCounter = new HashSet<>(); // set of unique observation unit ID's
        HashSet<String> gidCounter = new HashSet<>(); // set of unique GID's

        Map<String, PendingImportObject<BrAPIObservation>> observationByHash = pendingData.getObservationByHash();

        for (BrAPIImport row : importRows) {
            ExperimentObservation importRow = (ExperimentObservation) row;
            // Collect date for stats.
            addIfNotNull(environmentNameCounter, importRow.getEnv());
            addIfNotNull(obsUnitsIDCounter, ExperimentUtilities.createObservationUnitKey(importRow));
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
                observationByHash.values()
                        .stream()
                        .filter(preview -> preview != null && preview.getState() == ImportObjectState.EXISTING &&
                                !StringUtils.isBlank(preview.getBrAPIObject()
                                        .getValue()))
                        .count()
        );

        int numMutatedObservations = Math.toIntExact(
                observationByHash.values()
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

    public void addIfNotNull(HashSet<String> set, String setValue) {
        if (setValue != null) {
            set.add(setValue);
        }
    }
}
