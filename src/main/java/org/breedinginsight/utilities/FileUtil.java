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

package org.breedinginsight.utilities;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.ParsingExceptionType;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.json.JsonReadOptions;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class FileUtil {

    public static Table parseTableFromExcel(InputStream inputStream, Integer headerRowIndex) throws ParsingException {

        Workbook workbook = null;
        try {
            workbook = WorkbookFactory.create(inputStream);
        } catch (IOException | EncryptedDocumentException e) {
            log.error(e.getMessage());
            throw new ParsingException(ParsingExceptionType.ERROR_READING_FILE);
        }

        List<Sheet> sheets = new ArrayList<>();
        workbook.sheetIterator().forEachRemaining(sheets::add);
        //TODO: Gets the last sheet for now, do we want to allow them to specify which sheet to use?
        Sheet sheet = workbook.getSheetAt(sheets.size() - 1);
        Iterator<Row> rowIterator = sheet.rowIterator();

        // Get into format tablesaw can use
        //TODO: May want to keep excel types in the future
        Map<String, List<String>> columns;
        Row headerRow;
        DataFormatter formatter = new DataFormatter();
        try {
            columns = new HashMap<>();
            headerRow = sheet.getRow(headerRowIndex);
            headerRow.forEach(cell -> columns.put(formatter.formatCellValue(cell), new ArrayList<>()));
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                Iterator<Cell> cellIterator = row.cellIterator();
                for (int k=0; k < columns.values().size(); k++) {
                    Cell cell = row.getCell(k, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String header = formatter.formatCellValue(headerRow.getCell(k));
                    if (cell == null) {
                        columns.get(header).add(null);
                    } else if (cell.getCellType() == CellType.NUMERIC) {
                        double cellValue = cell.getNumericCellValue();
                        String stringValue = BigDecimal.valueOf(cellValue).stripTrailingZeros().toPlainString();
                        columns.get(header).add(stringValue);
                    } else {
                        columns.get(header).add(formatter.formatCellValue(cell));
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.toString());
            throw new ParsingException(ParsingExceptionType.ERROR_READING_FILE);
        }

        // Read from the excel header row to get proper order
        Table table = Table.create();
        Iterator<Cell> headerIterator = headerRow.cellIterator();
        HashSet<String> colNames = new HashSet<>();
        while (headerIterator.hasNext()) {
            Cell cell = headerIterator.next();
            StringColumn column = StringColumn.create(formatter.formatCellValue(cell), columns.get(formatter.formatCellValue(cell)));
            if (!colNames.add(column.name())) {
                throw new ParsingException(ParsingExceptionType.DUPLICATE_COLUMN_NAMES);
            }
            table.addColumns(column);
        }

        return removeNullRows(table);
    }

    public static Table parseTableFromCsv(InputStream inputStream) throws ParsingException {
        //TODO: See if this has the windows BOM issue
        try {
            Table df = Table.read().csv(inputStream);
            return removeNullRows(df);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new ParsingException(ParsingExceptionType.ERROR_READING_FILE);
        }
    }

    public static Table parseTableFromJson(String jsonString) throws ParsingException {
        try {
            return Table.read()
                    .usingOptions(
                            JsonReadOptions
                                    .builderFromString(jsonString)
                                    .columnTypesToDetect(List.of(ColumnType.STRING))
                    );
        } catch (IOException e) {
            throw new ParsingException(ParsingExceptionType.ERROR_READING_FILE);
        }

    }

    public static Table removeNullRows(Table table) {
        List<Integer> allNullRows = new ArrayList<>();
        // Find all null rows
        table.stream().forEach(row -> {
            Boolean allNull = true;
            for (String columnName: row.columnNames()) {
                if (row.getObject(columnName) != null && !row.getObject(columnName).toString().isEmpty()) {
                    allNull = false;
                }
            }
            if (allNull) {
                allNullRows.add(row.getRowNumber());
            }
        });

        if (allNullRows.size() > 0) {
            table = table.dropRows(allNullRows.stream().mapToInt(i->i).toArray());
        }
        return table;
    }
}
