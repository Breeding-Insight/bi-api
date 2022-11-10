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

import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.*;
import org.breedinginsight.brapps.importer.model.config.MappedImportRelation;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.model.mapping.MappingField;
import org.jooq.DSLContext;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

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
    public List<Column<?>> getDynamicColumns(Table data, String templateName) {
        List<ImportMapping> result = fileImportService.getSystemMappingByName(templateName);

        if (result.isEmpty()) {
            throw new InternalServerException("System mapping does not exist");
        }

        ImportMapping mapping = result.get(0);
        List<MappingField> config = mapping.getMappingConfig();
        List<String> columnNames = new ArrayList<>();

        for (MappingField field : config) {
            if (field.getValue() != null) {
                columnNames.add(field.getValue().getFileFieldName());
            }
        }

        List<String> names = data.columnNames();
        Collections.reverse(names);

        List<String> differences = names.stream()
                .filter(col -> !columnNames.contains(col))
                .collect(Collectors.toList());

        return data.columns(differences.toArray(String[]::new));
    }
}
