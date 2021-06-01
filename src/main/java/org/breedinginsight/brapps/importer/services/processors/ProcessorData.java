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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
//@Builder
@NoArgsConstructor
public class ProcessorData<T> {
    //private Map<String, PendingImportObject<T>> data;

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

    static <T> List<T> getNewObjects(Map<String, PendingImportObject<T>> objectsByName) {
        return objectsByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());
    }

}
