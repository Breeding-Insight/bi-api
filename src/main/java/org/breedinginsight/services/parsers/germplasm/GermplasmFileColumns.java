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
package org.breedinginsight.services.parsers.germplasm;

import org.breedinginsight.model.Column;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum GermplasmFileColumns {

    GID("GID", Column.ColumnDataType.INTEGER),
    NAME("Name", Column.ColumnDataType.STRING),
    BREEDING_METHOD("Breeding Method", Column.ColumnDataType.STRING),
    SOURCE("Source", Column.ColumnDataType.STRING),
    FEMALE_PARENT_GID("Female Parent GID", Column.ColumnDataType.INTEGER),
    MALE_PARENT_GID("Male Parent GID", Column.ColumnDataType.INTEGER),
    ENTRY_NO("Entry No", Column.ColumnDataType.INTEGER),
    FEMALE_PARENT_ENTRY_NO("Female Parent Entry No", Column.ColumnDataType.INTEGER),
    MALE_PARENT_ENTRY_NO("Male Parent Entry No", Column.ColumnDataType.INTEGER),
    EXTERNAL_UID("External UID", Column.ColumnDataType.STRING),
    SYNONYMS("Synonyms", Column.ColumnDataType.STRING);

    private final Column column;

    GermplasmFileColumns(String value, Column.ColumnDataType dataType) {
        this.column = new Column(value, dataType);
    }

    @Override
    public String toString() {
        return column.getValue();
    }

    public static List<Column> getOrderedColumns() {
        return Arrays.stream(GermplasmFileColumns.values())
                .map(value -> value.column)
                .collect(Collectors.toList());
    }
}