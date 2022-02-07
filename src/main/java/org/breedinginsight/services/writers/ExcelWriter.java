package org.breedinginsight.services.writers;

import io.micronaut.http.MediaType;
import io.micronaut.http.server.types.files.StreamedFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.breedinginsight.model.Column;

import java.io.*;
import java.util.*;

/*
 * This xlsx writer creates spreadsheets with the first row as column names and subsequent rows
 * as data.
 */
@Slf4j
public class ExcelWriter {

    private static final int EXCEL_COLUMN_NAMES_ROW = 0;

    //Writes a xlsx workbook with one sheet with desired columns and data
    public static XSSFWorkbook writeToWorkbook(String sheetName, List<Column> columns, List<Map<String, Object>> data) {
        //Create workbook
        XSSFWorkbook workbook = new XSSFWorkbook();

        //Create sheet
        XSSFSheet sheet = workbook.createSheet(sheetName);

        //Fill in header and data
        int cellCount;
        for(int i = 0; i < data.size() + 1; i++) {
            Row row = sheet.createRow(i);
            cellCount = 0;
            for (Column column : columns) {
                if (i==EXCEL_COLUMN_NAMES_ROW) {
                    //Column headers
                    row.createCell(cellCount).setCellValue(column.getValue());
                } else {
                    //Data values
                    if (data.get(i-1).get(column.getValue()) != null) {
                        if (column.getDataType() == Column.ColumnDataType.STRING) {
                            row.createCell(cellCount).setCellValue((String) data.get(i - 1).get(column.getValue()));
                        } else if (column.getDataType() == Column.ColumnDataType.NUMERICAL) {
                            row.createCell(cellCount).setCellValue((Double) data.get(i - 1).get(column.getValue()));
                        }
                    } else {
                        //Empty cell if no data
                        row.createCell(cellCount).setCellValue("");
                    }
                }
                cellCount++;
            }
        }

        return workbook;
    }

    public static StreamedFile writeToDownload(String sheetName, List<Column> columns, List<Map<String, Object>> data) throws IOException {
        XSSFWorkbook workbook = writeToWorkbook(sheetName, columns, data);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            InputStream inputStream = new ByteArrayInputStream(out.toByteArray());
            MediaType fileVal = new MediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
            StreamedFile downloadFile = new StreamedFile(inputStream, fileVal);
            return downloadFile;
        } catch (IOException e) {
            log.info(e.getMessage());
            throw e;
        }
    }

    //Writes doc to file in project, for optional testing
    public static void writeToFile(String fileName, String sheetName, List<Column> columns, List<Map<String, Object>> data) throws IOException {
        XSSFWorkbook workbook = writeToWorkbook(sheetName, columns, data);

        try (FileOutputStream fileOutput = new FileOutputStream(fileName +".xlsx")) {
            workbook.write(fileOutput);
            workbook.close();
        } catch (IOException e) {
            log.info(e.getMessage());
            throw e;
        }
    }
}
