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

import io.micronaut.http.server.types.files.StreamedFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.ParsingExceptionType;
import org.breedinginsight.services.writers.CSVWriter;
import org.breedinginsight.services.writers.ExcelWriter;
import tech.tablesaw.api.ColumnType;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;
import tech.tablesaw.io.csv.CsvReadOptions;
import tech.tablesaw.io.json.JsonReadOptions;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;


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
            // Build column map, throw if duplicate (non-blank) column header values are found.
            for (Cell cell: headerRow) {
                if (columns.containsKey(formatter.formatCellValue(cell)) && !formatter.formatCellValue(cell).isBlank()) {
                    // Duplicate (non-blank) column header found.
                    throw new ParsingException(ParsingExceptionType.DUPLICATE_COLUMN_NAMES);
                }
                columns.put(formatter.formatCellValue(cell), new ArrayList<>());
            }
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
                            stringValue = BigDecimal.valueOf(cellValue).stripTrailingZeros().toPlainString().trim();
                        }
                        columns.get(header).add(stringValue);
                    } else {
                        columns.get(header).add(formatter.formatCellValue(cell).trim());
                    }
                }
            }
        }
        catch (ParsingException e) {
            log.error(e.toString());
            throw e;
        }
        catch (Exception e) {
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
                        throw new ParsingException(ParsingExceptionType.MISSING_COLUMN_NAME);
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
        try {
            // Read inputStream into a String, exclude any Byte Order Marks.
            String input = new String(new BOMInputStream(inputStream, false).readAllBytes());

            // Check for duplicate (non-blank) column names, could also do other validations.
            try (CSVParser parser = CSVParser.parse(input, CSVFormat.EXCEL);) {
                HashSet<String> columnNames = new HashSet<>();
                for (String columnName: parser.getRecords().get(0)) {
                    if (columnName.isBlank()) {
                        log.debug("Skipping blank column header.");
                    } else {
                        log.debug("Column name: " + columnName);
                        if (columnNames.contains(columnName)) {
                            log.debug("Duplicate column header name: " + columnName);
                            throw new ParsingException(ParsingExceptionType.DUPLICATE_COLUMN_NAMES);
                        }
                        columnNames.add(columnName);
                    }
                }
            }

            // Convert to Table.
            //Jackson used downstream messily converts LOCAL_DATE/LOCAL_DATETIME, so need to interpret date input as strings
            //Note that if another type is needed later this is what needs to be updated
            //Removed FLOAT and DOUBLE types because it was causing issues with experiment geocoordinates which are to be treated as strings
            //until validations are done
            ArrayList<ColumnType> acceptedTypes = new ArrayList<>(Arrays.asList(ColumnType.STRING, ColumnType.INTEGER));
            Table df = Table.read().usingOptions(
                    CsvReadOptions
                            .builderFromString(input)
                            .columnTypesToDetect(acceptedTypes)
                            .separator(',')
            );
            return removeNullColumns(removeNullRows(df));
        } catch (ParsingException e) {
            throw e;
        } catch (Exception e) {
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
        } catch (Exception e) {
            log.debug(e.getMessage());
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
                    throw new ParsingException(ParsingExceptionType.MISSING_COLUMN_NAME);
                }
            }
            ++columnIndex;
        }

        table.removeColumns(columnsToRemove.toArray(Column[]::new));

        return table;
    }

    public static StreamedFile writeToStreamedFile(List<org.breedinginsight.model.Column> columns, List<Map<String, Object>> data, FileType extension, String sheetName) throws IOException {
        if (extension.equals(FileType.CSV)){
            return CSVWriter.writeToDownload(columns, data, extension);
        } else {
            return ExcelWriter.writeToDownload(sheetName, columns, data, extension);
        }
    }
}
