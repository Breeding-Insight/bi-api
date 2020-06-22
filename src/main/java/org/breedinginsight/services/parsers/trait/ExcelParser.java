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

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.breedinginsight.services.parsers.ParsingException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 *
 */
@Slf4j
public class ExcelParser {

    private static final int EXCEL_COLUMN_NAMES_ROW = 0;


    List<ExcelRecord> parse(Sheet sheet) throws ParsingException {

        List<ExcelRecord> records = new ArrayList<>();

        Row columnNames = sheet.getRow(EXCEL_COLUMN_NAMES_ROW);

        if (columnNames == null) {
            throw new ParsingException("Missing column names row");
        }

        Map<Integer, String> indexColNameMap = new HashMap<>();

        // get column name to index mapping
        for(int colIndex=0; colIndex<columnNames.getLastCellNum(); colIndex++) {
            Cell cell = columnNames.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            // TODO: exception if not text
            if (cell != null) {
                indexColNameMap.put(colIndex, cell.getStringCellValue());
            } else
            {
                log.info("Ignoring blank header column");
            }
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
}
