/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process;

import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

@Prototype
public class AppendStatistic {
    private HashSet<String> environmentNames;
    private HashSet<String> observationUnitIds;
    private HashSet<String> gids;
    private int newCount;
    private int existingCount;
    private int mutatedCount;

    public AppendStatistic() {
        this.clearData();
    }

    public void clearData() {
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
