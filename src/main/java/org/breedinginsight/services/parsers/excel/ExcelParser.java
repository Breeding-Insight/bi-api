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
package org.breedinginsight.services.parsers.excel;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.breedinginsight.services.parsers.ParsingException;

import java.util.*;

/*
 * This xls/xlsx parser expects spreadsheets with the first row as column names and subsequent rows
 * as data. Only data in a column with a column name will be kept, data outside these columns is
 * discarded.
 */
@Slf4j
public class ExcelParser {

    private static final int EXCEL_COLUMN_NAMES_ROW = 0;

    public static List<ExcelRecord> parse(Sheet sheet, Set<String> columns) throws ParsingException {

        List<ExcelRecord> records = new ArrayList<>();

        Row columnNames = sheet.getRow(EXCEL_COLUMN_NAMES_ROW);

        if (columnNames == null) {
            throw new ParsingException("Missing column names row");
        }

        Map<Integer, String> indexColNameMap = new HashMap<>();

        // get column name to index mapping
        for(int colIndex=0; colIndex<columnNames.getLastCellNum(); colIndex++) {
            Cell cell = columnNames.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell.getCellType() != CellType.STRING) {
                throw new ParsingException("Column name must be string cell");
            }
            if (cell != null) {
                indexColNameMap.put(colIndex, cell.getStringCellValue());
            } else
            {
                // not throwing an exception here in case they have empty cells to right of columns or even
                // between as we're not enforcing that, just that all the expected columns are present
                log.info("Ignoring blank header column");
            }
        }

        // check for duplicates
        if (hasDuplicates(new ArrayList(indexColNameMap.values()))) {
            throw new ParsingException("Found duplicate column names");
        }

        // check all column names were present
        if (!columns.stream().allMatch(col -> indexColNameMap.containsValue(col))) {
            throw new ParsingException("Missing expected columns");
        }

        // create a record for each row
        for (int rowIndex=EXCEL_COLUMN_NAMES_ROW+1; rowIndex<=sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            Map<String, Cell> data = new HashMap<>();
            for(int colIndex=0; colIndex<row.getLastCellNum(); colIndex++) {
                Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                data.put(indexColNameMap.get(colIndex), cell);
            }

            ExcelRecord record = new ExcelRecord(data);
            records.add(record);
        }

        return records;
    }

    private static boolean hasDuplicates(List<String> values) {
        Set<String> valuesSet = new HashSet<String>(values);
        if (values.size() != valuesSet.size()) {
            return true;
        }
        return false;
    }
}
