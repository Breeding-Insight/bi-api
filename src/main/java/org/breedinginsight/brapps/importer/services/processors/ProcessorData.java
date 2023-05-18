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
package org.breedinginsight.brapps.importer.services.processors;

import io.reactivex.functions.Function;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProcessorData {

    static <T, V> int getNumNewObjects(Map<V, PendingImportObject<T>> objectsByName) {
        long numNewObjects = objectsByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .count();
        return Math.toIntExact(numNewObjects);
    }

    static <T, V> int getNumExistingObjects(Map<V, PendingImportObject<T>> objectsByName) {
        long numExistingObjects = objectsByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.EXISTING)
                .count();
        return Math.toIntExact(numExistingObjects);
    }

    static <T, V> List<T> getNewObjects(Map<V, PendingImportObject<T>> objectsByName) {
        return objectsByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());
    }
    static <T, V> Map<String, T> getMutationsByObjectId(Map<V, PendingImportObject<T>> objectsByName, Function<T, String> dbIdFilter) {
        return objectsByName.entrySet().stream()
                .filter(entry -> ImportObjectState.MUTATED == entry.getValue().getState())
                .collect(Collectors
                        .toMap(entry -> {
                                    try {
                                        return dbIdFilter.apply(entry.getValue().getBrAPIObject());
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                },
                                entry -> entry.getValue().getBrAPIObject()));
    }
}
