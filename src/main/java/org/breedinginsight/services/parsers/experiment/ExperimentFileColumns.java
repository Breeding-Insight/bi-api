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
package org.breedinginsight.services.parsers.experiment;

import com.sun.mail.imap.protocol.ENVELOPE;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.model.Column;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ExperimentFileColumns {

    GERMPLASM_NAME(ExperimentObservation.Columns.GERMPLASM_NAME, Column.ColumnDataType.STRING),
    GERMPLASM_GID(ExperimentObservation.Columns.GERMPLASM_GID, Column.ColumnDataType.STRING),
    TEST_CHECK(ExperimentObservation.Columns.TEST_CHECK, Column.ColumnDataType.STRING),
    EXP_TITLE(ExperimentObservation.Columns.EXP_TITLE, Column.ColumnDataType.STRING),
    EXP_DESCRIPTION(ExperimentObservation.Columns.EXP_DESCRIPTION, Column.ColumnDataType.STRING),
    EXP_UNIT(ExperimentObservation.Columns.EXP_UNIT, Column.ColumnDataType.STRING),
    EXP_TYPE(ExperimentObservation.Columns.EXP_TYPE, Column.ColumnDataType.STRING),
    ENV(ExperimentObservation.Columns.ENV, Column.ColumnDataType.STRING),
    ENV_LOCATION(ExperimentObservation.Columns.ENV_LOCATION, Column.ColumnDataType.STRING),
    ENV_YEAR(ExperimentObservation.Columns.ENV_YEAR, Column.ColumnDataType.INTEGER),
    EXP_UNIT_ID(ExperimentObservation.Columns.EXP_UNIT_ID, Column.ColumnDataType.STRING),
    REP_NUM(ExperimentObservation.Columns.REP_NUM, Column.ColumnDataType.INTEGER),
    BLOCK_NUM(ExperimentObservation.Columns.BLOCK_NUM, Column.ColumnDataType.INTEGER),
    ROW(ExperimentObservation.Columns.ROW, Column.ColumnDataType.STRING),
    COLUMN(ExperimentObservation.Columns.COLUMN, Column.ColumnDataType.STRING),
    TREATMENT_FACTORS(ExperimentObservation.Columns.TREATMENT_FACTORS, Column.ColumnDataType.STRING),
    OBS_UNIT_ID(ExperimentObservation.Columns.OBS_UNIT_ID, Column.ColumnDataType.STRING);

    private final Column column;

    ExperimentFileColumns(String value, Column.ColumnDataType dataType) {
        this.column = new Column(value, dataType);
    }

    @Override
    public String toString() {
        return column.getValue();
    }

    public static List<Column> getOrderedColumns() {
        return Arrays.stream(ExperimentFileColumns.values())
                .map(value -> value.column)
                .collect(Collectors.toList());
    }
}