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

import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.ParsingExceptionType;
import org.breedinginsight.services.parsers.excel.ExcelParser;
import org.breedinginsight.services.parsers.excel.ExcelRecord;
import org.breedinginsight.services.parsers.trait.TraitFileColumns;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;
import tech.tablesaw.columns.Column;

import java.io.*;
import java.util.*;

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
        try {
            columns = new HashMap<>();
            headerRow = sheet.getRow(headerRowIndex);
            headerRow.forEach(cell -> columns.put(cell.getStringCellValue(), new ArrayList<>()));
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                Iterator<Cell> cellIterator = row.cellIterator();
                for (int k=0; k < columns.values().size(); k++) {
                    Cell cell = row.getCell(k, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String header = headerRow.getCell(k).getStringCellValue();
                    if (cell == null) {
                        columns.get(header).add(null);
                    } else if (cell.getCellType() == CellType.NUMERIC) {
                        columns.get(header).add(String.valueOf(cell.getNumericCellValue()));
                    } else {
                        columns.get(header).add(cell.getStringCellValue());
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
        while (headerIterator.hasNext()) {
            Cell cell = headerIterator.next();
            StringColumn column = StringColumn.create(cell.getStringCellValue(), columns.get(cell.getStringCellValue()));
            table.addColumns(column);
        }

        return table;
    }

    public static Table parseTableFromCsv(InputStream inputStream) throws ParsingException {
        //TODO: See if this has the windows BOM issue
        try {
            Table df = Table.read().csv(inputStream);
            return df;
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new ParsingException(ParsingExceptionType.ERROR_READING_FILE);
        }
    }

}
