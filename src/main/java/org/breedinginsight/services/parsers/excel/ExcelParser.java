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
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.ParsingExceptionType;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.*;

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
            throw new ParsingException(ParsingExceptionType.MISSING_COLUMN_NAMES);
        }

        Map<Integer, String> indexColNameMap = new HashMap<>();

        // get column name to index mapping
        for(int colIndex=0; colIndex<columnNames.getLastCellNum(); colIndex++) {
            Cell cell = columnNames.getCell(colIndex);
            if (cell.getCellType() != CellType.STRING && cell.getCellType() != CellType.BLANK) {
                throw new ParsingException(ParsingExceptionType.COLUMN_NAME_NOT_STRING);
            }
            if (cell != null && isNotBlank(cell.getStringCellValue())) {
                indexColNameMap.put(colIndex, cell.getStringCellValue().toLowerCase());
            } else
            {
                // not throwing an exception here in case they have empty cells to right of columns or even
                // between as we're not enforcing that, just that all the expected columns are present
                log.info("Ignoring blank header column");
            }
        }

        // check for duplicate column names
        if (hasDuplicates(new ArrayList(indexColNameMap.values()))) {
            throw new ParsingException(ParsingExceptionType.DUPLICATE_COLUMN_NAMES);
        }

        // check all column names were present
        List<String> missingColumns = columns.stream().filter(col -> !indexColNameMap.containsValue(col.toLowerCase())).collect(Collectors.toList());
        if (missingColumns.size() > 0){
            throw new ParsingException(ParsingExceptionType.MISSING_EXPECTED_COLUMNS, missingColumns);
        }

        // create a record for each row
        for (int rowIndex=EXCEL_COLUMN_NAMES_ROW+1; rowIndex<=sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            Map<String, Cell> data = new HashMap<>();

            if (row == null){
                throw new ParsingException(ParsingExceptionType.EMPTY_ROW);
            }

            for(int colIndex=0; colIndex<row.getLastCellNum(); colIndex++) {
                Cell cell = row.getCell(colIndex);
                data.put(indexColNameMap.get(colIndex), cell);
            }

            records.add(new ExcelRecord(data));

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
