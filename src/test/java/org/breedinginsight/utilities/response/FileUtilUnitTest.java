package org.breedinginsight.utilities.response;

import lombok.SneakyThrows;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.ParsingExceptionType;
import org.breedinginsight.utilities.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import tech.tablesaw.api.Table;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

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
}
