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
import tech.tablesaw.columns.Column;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.io.json.JsonReadOptions;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public class FileUtil {
    public static final String EXCEL_DATA_SHEET_NAME = "Data";
    // For backward compatibility
    private static final String OLD_GERMPLASM_EXCEL_DATA_SHEET_NAME = "Germplasm Import";
    private static final String OLD_EXPERIMENT_EXCEL_DATA_SHEET_NAME = "Experiment Data";

    public static Table parseTableFromExcel(InputStream inputStream, Integer headerRowIndex) throws ParsingException {

        Workbook workbook = null;
        try {
            workbook = WorkbookFactory.create(inputStream);
        } catch (IOException | EncryptedDocumentException e) {
            log.error(e.getMessage());
            throw new ParsingException(ParsingExceptionType.ERROR_READING_FILE);
        }

        Sheet sheet = workbook.getSheet(EXCEL_DATA_SHEET_NAME);

        //For backward compatibility allow old sheet names
        if( sheet == null){ sheet = workbook.getSheet(OLD_GERMPLASM_EXCEL_DATA_SHEET_NAME); }
        if( sheet == null){ sheet = workbook.getSheet(OLD_EXPERIMENT_EXCEL_DATA_SHEET_NAME); }

        if (sheet == null) {
            throw new ParsingException(ParsingExceptionType.MISSING_SHEET);
        }
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
                        //Distinguish between date and numeric
                        DataFormatter dataFormatter = new DataFormatter();
                        String stringValue = dataFormatter.formatCellValue(cell);
                        if (!stringValue.contains("-")) {
                            //No dashes, assume cell is numeric and not date
                            double cellValue = cell.getNumericCellValue();
                            stringValue = BigDecimal.valueOf(cellValue).stripTrailingZeros().toPlainString();
                        }
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
            // Drop columns with no data, throw exception if column has data but no header.
            if (cell.getCellType() == CellType.BLANK)
            {
                // If data in column with no header, throw parsing exception, user likely wants to add header.
                for (String value : column.asList()) {
                    if (!value.isBlank())
                    {
                        throw new ParsingException(ParsingExceptionType.MISSING_COLUMN_HEADER);
                    }
                }
                // Silently drop columns with neither headers nor data, user likely doesn't know they exist.
                continue;
            }
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
            //Jackson used downstream messily converts LOCAL_DATE/LOCAL_DATETIME, so need to interpret date input as strings
            //Note that if another type is needed later this is what needs to be updated
            ArrayList<ColumnType> acceptedTypes = new ArrayList<>(Arrays.asList(ColumnType.STRING, ColumnType.INTEGER, ColumnType.DOUBLE, ColumnType.FLOAT));
            Table df = Table.read().usingOptions(
                    CsvReadOptions
                            .builder(inputStream)
                            .columnTypesToDetect(acceptedTypes)
                            .separator(',')
            );
            return removeNullColumns(removeNullRows(df));
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
            boolean allNull = true;
            for (String columnName: row.columnNames()) {
                if (row.getObject(columnName) != null && !row.getObject(columnName).toString().isEmpty()) {
                    allNull = false;
                    break;
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

    /** Removes columns with an empty or null header and no data from a table. */
    public static Table removeNullColumns(Table table) throws ParsingException {
        ArrayList<Column> columnsToRemove = new ArrayList<>();
        int columnIndex = 0;
        for (Column column : table.columns()) {
            // Empty/null column headers are replaced with a placeholder by tablesaw, e.g. "C23" for the 23rd column.
            // See https://github.com/jtablesaw/tablesaw/blob/42ca803e1a5fff1d4a01f5a3deabc38ced783125/core/src/main/java/tech/tablesaw/io/FileReader.java#L101.
            String placeholderName = String.format("C%d", columnIndex);
            if (column.name().equals(placeholderName)) {
                if (column.countMissing() == column.size()) {
                    // Silently drop columns with neither headers nor data, user likely doesn't know they exist.
                    columnsToRemove.add(column);
                }
                else {
                    // If data in column with no header, throw parsing exception, user likely wants to add header.
                    throw new ParsingException(ParsingExceptionType.MISSING_COLUMN_HEADER);
                }
            }
            ++columnIndex;
        }

        table.removeColumns(columnsToRemove.toArray(Column[]::new));

        return table;
    }
}
