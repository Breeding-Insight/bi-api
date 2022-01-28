package org.breedinginsight.services.writers;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.breedinginsight.model.Column;
import org.breedinginsight.services.parsers.excel.ExcelRecord;

import java.io.FileOutputStream;
import java.util.*;

/*
 * This xlsx writer creates spreadsheets with the first row as column names and subsequent rows
 * as data.
 */
@Slf4j
public class ExcelWriter {

    private static final int EXCEL_COLUMN_NAMES_ROW = 0;

    //Writes a xlsx file with one sheet with desired columns and data
    public static void write(String fileName, String sheetName, List<Column> columns, List<Map<String, Object>> data) {
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
                    //TODO can likely clean
                    if (column.getDataType()==Column.ColumnDataType.STRING) {
                        row.createCell(cellCount).setCellValue((String) data.get(i-1).get(column));
                    } else if (column.getDataType()==Column.ColumnDataType.NUMERICAL) {
                        row.createCell(cellCount).setCellValue((Float) data.get(i-1).get(column));
                    }
                }
            }
        }

        //Column Type, Name and Data Type

        //Write to file
        try (FileOutputStream fileOutput = new FileOutputStream(fileName +".xlsx")) {
            workbook.write(fileOutput);
        } catch (Exception e) {
            e.printStackTrace();
            //TODO error handling
        }
    }
}
