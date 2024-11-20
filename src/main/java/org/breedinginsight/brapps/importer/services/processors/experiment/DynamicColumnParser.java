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
package org.breedinginsight.brapps.importer.services.processors.experiment;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import lombok.Getter;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import java.util.ArrayList;
import java.util.List;

import static org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities.TIMESTAMP_PREFIX;

public class DynamicColumnParser {

    /**
     * Parses dynamic columns from a table and separates them into phenotype and timestamp columns.
     *
     * @param data              The table containing the dynamic columns.
     * @param dynamicColumnNames An array of dynamic column names to be parsed.
     * @return A DynamicColumnParseResult object containing the parsed phenotype and timestamp columns.
     */
    public static DynamicColumnParseResult parse(Table data, String[] dynamicColumnNames) {

        // don't allow periods (.) or square brackets in Dynamic Column Names
        for (String dynamicColumnName: dynamicColumnNames) {
            if(dynamicColumnName.contains(".") || dynamicColumnName.contains("[") || dynamicColumnName.contains("]")){
                String errorMsg = String.format("Observation columns may not contain periods or square brackets (see column '%s')", dynamicColumnName);
                throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, errorMsg);
            }
        }

        List<Column<?>> dynamicCols = data.columns(dynamicColumnNames);
        List<Column<?>> phenotypeCols = new ArrayList<>();
        List<Column<?>> timestampCols = new ArrayList<>();

        for (Column<?> dynamicCol : dynamicCols) {
            if (dynamicCol.name().startsWith(TIMESTAMP_PREFIX)) {
                timestampCols.add(dynamicCol);
            } else {
                phenotypeCols.add(dynamicCol);
            }
        }

        return new DynamicColumnParseResult(phenotypeCols, timestampCols);
    }

    @Getter
    public static class DynamicColumnParseResult {
        private final List<Column<?>> phenotypeCols;
        private final List<Column<?>> timestampCols;

        public DynamicColumnParseResult(List<Column<?>> phenotypeCols, List<Column<?>> timestampCols) {
            this.phenotypeCols = phenotypeCols;
            this.timestampCols = timestampCols;
        }

    }
}