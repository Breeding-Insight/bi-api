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

package org.breedinginsight.brapps.importer.services;

import io.reactivex.functions.Function;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.breedinginsight.brapps.importer.model.config.MappedImportRelation;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class FileMappingUtil {

    public static final String EXPERIMENT_TEMPLATE_NAME = "ExperimentsTemplateMap";
    private FileImportService fileImportService;


    @Inject
    public FileMappingUtil(FileImportService fileImportService) {
        this.fileImportService = fileImportService;
    }

    // Returns a list of integers to identify the target row of the relationship. -1 if no relationship was found
    public List<Pair<Integer, String>> findFileRelationships(Table data, List<MappedImportRelation> importRelations) {

        List<Pair<Integer, String>> targetIndexList = new ArrayList<>();
        String targetColumn = importRelations.get(0).getTargetColumn();

        // Construct a map of the target row values
        Map<String, Integer> targetRowMap = new HashMap<>();
        for (int k = 0; k < data.rowCount(); k++) {
            Row targetRow = data.row(k);
            String targetValue = targetRow.getString(targetColumn);
            if (targetValue == null) continue;
            targetRowMap.put(targetValue, k);
        }

        for (int i = 0; i < data.rowCount(); i++) {
            // Look for a match
            if (importRelations.get(i) == null) {
                targetIndexList.add(new MutablePair<>(-1, null));
            } else {
                String referenceValue = importRelations.get(i).getReferenceValue();
                if (targetRowMap.containsKey(referenceValue)) {
                    targetIndexList.add(new MutablePair<>(targetRowMap.get(referenceValue), referenceValue));
                } else {
                    targetIndexList.add(new MutablePair<>(-1, referenceValue));
                }
            }
        }

        return targetIndexList;
    }

    public <T> List<T> sortByField(List<String> sortedFields, List<T> unsortedItems, Function<T, String> fieldGetter) {
        CaseInsensitiveMap<String, Integer> sortOrder = new CaseInsensitiveMap<>();
        for (int i = 0; i < sortedFields.size(); i++) {
            sortOrder.put(sortedFields.get(i), i);
        }

        unsortedItems.sort((i1, i2) -> {
            try {
                String field1 = fieldGetter.apply(i1);
                String field2 = fieldGetter.apply(i2);
                return Integer.compare(sortOrder.get(field1), sortOrder.get(field2));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return unsortedItems;
    }
}
