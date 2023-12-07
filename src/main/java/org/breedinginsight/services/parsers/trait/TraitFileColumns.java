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
package org.breedinginsight.services.parsers.trait;

import org.breedinginsight.model.Column;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum TraitFileColumns {

    NAME("Name", Column.ColumnDataType.STRING),
    FULL_NAME("Full name", Column.ColumnDataType.STRING),
    TERM_TYPE("Term Type", Column.ColumnDataType.STRING),
    DESCRIPTION("Description", Column.ColumnDataType.STRING),
    SYNONYMS("Synonyms", Column.ColumnDataType.STRING),
    STATUS("Status",Column.ColumnDataType.STRING),
    TAGS("Tags", Column.ColumnDataType.STRING),
    TRAIT_ENTITY("Trait entity", Column.ColumnDataType.STRING),
    TRAIT_ATTRIBUTE("Trait attribute", Column.ColumnDataType.STRING),
    METHOD_DESCRIPTION("Method description", Column.ColumnDataType.STRING),
    METHOD_CLASS("Method class", Column.ColumnDataType.STRING),
    METHOD_FORMULA("Method formula", Column.ColumnDataType.STRING),
    SCALE_CLASS("Scale class", Column.ColumnDataType.STRING),
    SCALE_NAME("Units", Column.ColumnDataType.STRING),
    SCALE_DECIMAL_PLACES("Scale decimal places", Column.ColumnDataType.INTEGER),
    SCALE_LOWER_LIMIT("Scale lower limit", Column.ColumnDataType.INTEGER),
    SCALE_UPPER_LIMIT("Scale upper limit", Column.ColumnDataType.INTEGER),
    SCALE_CATEGORIES("Scale categories", Column.ColumnDataType.STRING);

    private final Column column;

    TraitFileColumns(String value, Column.ColumnDataType dataType) {
        this.column = new Column(value, dataType);
    }

    @Override
    public String toString() {
        return column.getValue();
    }

    public static Set<String> getColumnNames() {
        return Arrays.stream(TraitFileColumns.values())
                .map(value -> value.column.getValue())
                .collect(Collectors.toSet());
    }

    public static List<Column> getOrderedColumns() {
        return Arrays.stream(TraitFileColumns.values())
                .map(value -> value.column)
                .collect(Collectors.toList());
    }
}