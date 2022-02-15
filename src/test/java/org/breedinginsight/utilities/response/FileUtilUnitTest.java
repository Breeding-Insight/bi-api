package org.breedinginsight.utilities.response;

import lombok.SneakyThrows;
import org.breedinginsight.model.Column;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.ParsingExceptionType;
import org.breedinginsight.services.writers.ExcelWriter;
import org.breedinginsight.utilities.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tech.tablesaw.api.Table;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FileUtilUnitTest {

    @Test
    @SneakyThrows
    void parseCsvRemoveAllNullRows() {
        File file = new File("src/test/resources/files/fileutil/file_all_null_rows.csv");
        InputStream inputStream = new FileInputStream(file);
        Table resultTable = FileUtil.parseTableFromCsv(inputStream);
        assertEquals(2, resultTable.rowCount(), "Wrong number of rows were parsed");
    }

    @Test
    @SneakyThrows
    void parseExcelRemoveAllNullRows() {
        File file = new File("src/test/resources/files/fileutil/file_all_null_rows.xls");
        InputStream inputStream = new FileInputStream(file);
        Table resultTable = FileUtil.parseTableFromExcel(inputStream, 0);
        assertEquals(2, resultTable.rowCount(), "Wrong number of rows were parsed");
    }


    @Test
    @SneakyThrows
    void parseCsvNoRemoveSomeNullRows() {
        File file = new File("src/test/resources/files/fileutil/file_some_null_rows.csv");
        InputStream inputStream = new FileInputStream(file);
        Table resultTable = FileUtil.parseTableFromCsv(inputStream);
        assertEquals(10, resultTable.rowCount(), "Wrong number of rows were parsed");
    }

    @Test
    @SneakyThrows
    void parseExcelNoRemoveSomeNullRows() {
        File file = new File("src/test/resources/files/fileutil/file_some_null_rows.xls");
        InputStream inputStream = new FileInputStream(file);
        Table resultTable = FileUtil.parseTableFromExcel(inputStream, 0);
        assertEquals(5, resultTable.rowCount(), "Wrong number of rows were parsed");
    }

    @Test
    @SneakyThrows
    void writeExcelCheckColumns() {
        List<Column> columns = new ArrayList<>();
        columns.add(new Column("Test A", Column.ColumnDataType.STRING));
        columns.add(new Column("Test B", Column.ColumnDataType.NUMERICAL));
        columns.add(new Column("Test C", Column.ColumnDataType.STRING));

        List<String> columnNames = new ArrayList<>();
        columnNames.add("Test A");
        columnNames.add("Test B");
        columnNames.add("Test C");

        List<Map<String, Object>> data =  new ArrayList<>();
        HashMap row = new HashMap<>();
        row.put("Test A", "Data A");
        row.put("Test B", Double.valueOf("2"));
        row.put("Test C", "C");
        data.add(row);

        InputStream inputStream = ExcelWriter.writeToInputStream("SheetName", columns, data);
        Table resultTable = FileUtil.parseTableFromExcel(inputStream, 0);

        assertEquals(1, resultTable.rowCount(), "Wrong number of rows were exported");
        assertEquals(columnNames, resultTable.columnNames(), "Incorrect columns were exported");
        assertEquals("Data A", resultTable.get(0, 0), "Incorrect data exported");
        assertEquals("2", resultTable.get(0, 1), "Incorrect data exported");
        assertEquals("C", resultTable.get(0, 2), "Incorrect data exported");
    }
}
