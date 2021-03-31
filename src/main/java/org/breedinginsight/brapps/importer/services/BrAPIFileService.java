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

import org.breedinginsight.brapps.importer.model.config.ImportRelation;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class BrAPIFileService {

    // Returns a list of integers to identify the target row of the relationship. -1 if no relationship was found
    public List<Integer> findFileRelationships(Table data, ImportRelation importRelation) {

        List<Integer> targetIndexList = new ArrayList<>();
        for (int i = 0; i < data.rowCount(); i++) {
            String targetColumn = importRelation.getTarget();
            // Construct a map of the target row values
            Map<String, Integer> targetRowMap = new HashMap<>();
            for (int k = 0; k < data.rowCount(); k++) {
                Row targetRow = data.row(k);
                String targetValue = targetRow.getString(targetColumn);
                targetRowMap.put(targetValue, k);
            }

            // Look for a match
            Row referenceRow = data.row(i);
            String referenceColumn = importRelation.getReference();
            String referenceValue = referenceRow.getString(referenceColumn);
            if (targetRowMap.containsKey(referenceValue)) {
                targetIndexList.add(targetRowMap.get(referenceValue));
            } else {
                targetIndexList.add(-1);
            }
        }

        return targetIndexList;
    }
}
