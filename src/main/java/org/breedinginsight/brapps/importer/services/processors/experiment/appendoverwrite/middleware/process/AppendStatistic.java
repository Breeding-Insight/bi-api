package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

@Prototype
public class AppendStatistic {
    private final HashSet<String> environmentNames;
    private final HashSet<String> observationUnitIds;
    private final HashSet<String> gids;
    private int newCount;
    private int existingCount;
    private int mutatedCount;

    public AppendStatistic() {
        this.environmentNames = new HashSet<>();
        this.observationUnitIds = new HashSet<>();
        this.gids = new HashSet<>();
        this.newCount = 0;
        this.existingCount = 0;
        this.mutatedCount = 0;
    }

    public int incrementNewCount(Integer value) {
        int increment = 0;
        if (value == null) {
            increment = 1;
        } else if (value >= 0) {
            increment = value;
        }
        this.newCount += increment;

        return this.newCount;
    }
    public int incrementExistingCount(Integer value) {
        int increment = 0;
        if (value == null) {
            increment = 1;
        } else if (value >= 0) {
            increment = value;
        }
        this.existingCount += increment;

        return this.existingCount;
    }
    public int incrementMutatedCount(Integer value) {
        int increment = 0;
        if (value == null) {
            increment = 1;
        } else if (value >= 0) {
            increment = value;
        }
        this.mutatedCount += increment;

        return this.mutatedCount;
    }
    public void addEnvironmentName(String name) {
        Optional.ofNullable(name).ifPresent(environmentNames::add);
    }
    public void addObservationUnitId(String id) {
        Optional.ofNullable(id).ifPresent(observationUnitIds::add);
    }
    public void addGid(String gid) {
        Optional.ofNullable(gid).ifPresent(gids::add);
    }
    public Map<String, ImportPreviewStatistics> constructPreviewMap() {
        ImportPreviewStatistics environmentStats = ImportPreviewStatistics.builder().newObjectCount(environmentNames.size()).build();
        ImportPreviewStatistics observationUnitsStats = ImportPreviewStatistics.builder().newObjectCount(observationUnitIds.size()).build();
        ImportPreviewStatistics gidStats = ImportPreviewStatistics.builder().newObjectCount(gids.size()).build();
        ImportPreviewStatistics newStats = ImportPreviewStatistics.builder().newObjectCount(newCount).build();
        ImportPreviewStatistics existingStats = ImportPreviewStatistics.builder().newObjectCount(existingCount).build();
        ImportPreviewStatistics mutatedStats = ImportPreviewStatistics.builder().newObjectCount(mutatedCount).build();

        return Map.of(
                "Environments", environmentStats,
                "Observation_Units", observationUnitsStats,
                "GIDs", gidStats,
                "Observations", newStats,
                "Existing_Observations", existingStats,
                "Mutated_Observations", mutatedStats
        );
    }
}
