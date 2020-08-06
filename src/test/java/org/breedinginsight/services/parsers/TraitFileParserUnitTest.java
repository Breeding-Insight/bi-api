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
package org.breedinginsight.services.parsers;

import lombok.SneakyThrows;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.parsers.trait.TraitFileParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TraitFileParserUnitTest {

    private TraitFileParser parser;

    @BeforeAll
    void setup() {
        parser = new TraitFileParser();
    }

    @Test
    @SneakyThrows
    void parseCsvMissingColumnData() {
        File file = new File("src/test/resources/files/missing_method_name_with_data.csv");
        InputStream inputStream = new FileInputStream(file);
        ParsingException e = assertThrows(ParsingException.class, () -> parser.parseCsv(inputStream), "expected parsing exception");
        assertEquals(ParsingExceptionType.MISSING_EXPECTED_COLUMNS, e.getType(), "Wrong type");
    }

    @Test
    @SneakyThrows
    void parseCsvColumnsNoData() {
        File file = new File("src/test/resources/files/columns_no_data.csv");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseCsv(inputStream);

        assertEquals(true, traits.isEmpty(), "traits were not empty");
    }

    @Test
    @SneakyThrows
    void parseCsvEmptyFile() {
        File file = new File("src/test/resources/files/empty.csv");
        InputStream inputStream = new FileInputStream(file);

        ParsingException e = assertThrows(ParsingException.class, () -> parser.parseCsv(inputStream), "expected parsing exception");
        assertEquals(ParsingExceptionType.MISSING_COLUMN_NAMES, e.getType(), "Wrong type");
    }

    @Test
    @SneakyThrows
    void parseCsvMissingColumnNoData() {
        File file = new File("src/test/resources/files/missing_column.csv");
        InputStream inputStream = new FileInputStream(file);

        ParsingException e = assertThrows(ParsingException.class, () -> parser.parseCsv(inputStream), "expected parsing exception");
        assertEquals(ParsingExceptionType.MISSING_EXPECTED_COLUMNS, e.getType(), "Wrong type");
    }

    @Test
    @SneakyThrows
    void parseCsvDuplicateColumnName() {
        File file = new File("src/test/resources/files/data_duplicate_method_name.csv");
        InputStream inputStream = new FileInputStream(file);

        ParsingException e = assertThrows(ParsingException.class, () -> parser.parseCsv(inputStream), "expected parsing exception");
        assertEquals(ParsingExceptionType.DUPLICATE_COLUMN_NAMES, e.getType(), "Wrong type");
    }

    @Test
    @SneakyThrows
    void parseCsvEmptyMultipleColumnNames() {
        File file = new File("src/test/resources/files/data_one_row_empty_headers.csv");
        InputStream inputStream = new FileInputStream(file);

        List<Trait> traits = parser.parseCsv(inputStream);
        assertEquals(1, traits.size(), "number of traits different than expected");
    }

    // repeating this test for excel because blanks must be mixed in with populated columns because the
    // parser will not look at blanks past the end of populated columns in the excel format
    @Test
    @SneakyThrows
    void parseExcelEmptyMultipleColumnNames() {
        File file = new File("src/test/resources/files/data_one_row_empty_headers.xls");
        InputStream inputStream = new FileInputStream(file);

        List<Trait> traits = parser.parseExcel(inputStream);
        assertEquals(1, traits.size(), "number of traits different than expected");
    }

    // will error out if a column name is not a string or blank type
    @Test
    @SneakyThrows
    void parseExcelEmptyFormulaColumnNames() {
        File file = new File("src/test/resources/files/data_one_row_empty_formula_headers.xls");
        InputStream inputStream = new FileInputStream(file);

        ParsingException e = assertThrows(ParsingException.class, () -> parser.parseExcel(inputStream), "expected parsing exception");
        assertEquals(ParsingExceptionType.COLUMN_NAME_NOT_STRING, e.getType(), "Wrong type");
    }

    @Test
    @SneakyThrows
    void parseCsvActiveColumnInvalidValue() {
        File file = new File("src/test/resources/files/data_one_row_invalid_active_value.csv");
        InputStream inputStream = new FileInputStream(file);

        ParsingException e = assertThrows(ParsingException.class, () -> parser.parseCsv(inputStream), "expected parsing exception");
        assertEquals(ParsingExceptionType.INVALID_TRAIT_STATUS, e.getType(), "Wrong type");
    }

    @Test
    @SneakyThrows
    void parseCsvActiveColumnBlank() {
        File file = new File("src/test/resources/files/data_one_row_blank_active_value.csv");
        InputStream inputStream = new FileInputStream(file);

        List<Trait> traits = parser.parseCsv(inputStream);
        assertEquals(1, traits.size(), "number of traits different than expected");
    }

    // repeating for excel here just to make sure blanks are handled properly in case of any future regressions
    @Test
    @SneakyThrows
    void parseExcelActiveColumnBlank() {
        File file = new File("src/test/resources/files/data_one_row_empty_active_value.xls");
        InputStream inputStream = new FileInputStream(file);

        List<Trait> traits = parser.parseExcel(inputStream);
        assertEquals(1, traits.size(), "number of traits different than expected");
    }

    @Test
    @SneakyThrows
    void parseCsvScaleClassInvalidValue() {
        File file = new File("src/test/resources/files/data_one_row_invalid_scale_class.csv");
        InputStream inputStream = new FileInputStream(file);

        ParsingException e = assertThrows(ParsingException.class, () -> parser.parseCsv(inputStream), "expected parsing exception");
        assertEquals(ParsingExceptionType.INVALID_SCALE_CLASS, e.getType(), "Wrong type");
    }

    @Test
    @SneakyThrows
    void parseCsvScaleClassBlank() {
        File file = new File("src/test/resources/files/data_one_row_blank_scale_class.csv");
        InputStream inputStream = new FileInputStream(file);

        ParsingException e = assertThrows(ParsingException.class, () -> parser.parseCsv(inputStream), "expected parsing exception");
        assertEquals(ParsingExceptionType.MISSING_SCALE_CLASS, e.getType(), "Wrong type");
    }

    @Test
    @SneakyThrows
    void parseCsvScaleDecimalPlacesInvalid() {
        File file = new File("src/test/resources/files/data_one_row_scale_decimal_invalid.csv");
        InputStream inputStream = new FileInputStream(file);

        ParsingException e = assertThrows(ParsingException.class, () -> parser.parseCsv(inputStream), "expected parsing exception");
        assertEquals(ParsingExceptionType.INVALID_SCALE_DECIMAL_PLACES, e.getType(), "Wrong type");
    }

    @Test
    @SneakyThrows
    void parseCsvScaleDecimalPlacesBlank() {
        File file = new File("src/test/resources/files/data_one_row_scale_decimal_blank.csv");
        InputStream inputStream = new FileInputStream(file);

        List<Trait> traits = parser.parseCsv(inputStream);
        assertEquals(1, traits.size(), "number of traits different than expected");
    }

    @Test
    @SneakyThrows
    void parseCsvScaleCategoriesBlank() {
        File file = new File("src/test/resources/files/data_one_row_scale_categories_blank.csv");
        InputStream inputStream = new FileInputStream(file);

        List<Trait> traits = parser.parseCsv(inputStream);
        assertEquals(1, traits.size(), "number of traits different than expected");
    }

    // Not repeating error tests for parseExcel in the interest of time and it using the same underlying parsing code

    @Test
    @SneakyThrows
    void parseCsvSingleRowSuccess() {
        File file = new File("src/test/resources/files/data_one_row.csv");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseCsv(inputStream);

        assertEquals(1, traits.size(), "number of traits different than expected");

        Trait trait = traits.get(0);
        assertTestTraitEquals(trait);
    }

    @Test
    @SneakyThrows
    void parseXlsSingleRowSuccess() {
        File file = new File("src/test/resources/files/data_one_row.xls");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseExcel(inputStream);

        assertEquals(1, traits.size(), "number of traits different than expected");

        Trait trait = traits.get(0);
        assertTestTraitEquals(trait);
    }

    @Test
    @SneakyThrows
    void parseXlsxSingleRowSuccess() {
        File file = new File("src/test/resources/files/data_one_row.xlsx");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseExcel(inputStream);

        assertEquals(1, traits.size(), "number of traits different than expected");

        Trait trait = traits.get(0);
        assertTestTraitEquals(trait);
    }

    private void assertTestTraitEquals(Trait trait) {
        List<String> abbreviations = Arrays.asList(trait.getAbbreviations());
        assertEquals(2, abbreviations.size(), "number of abbreviations different than expected");
        List<String> synonyms = trait.getSynonyms();
        assertEquals(2, synonyms.size(), "number of synonyms different than expected");

        assertEquals("Powdery Mildew severity field, leaves", trait.getTraitName(), "wrong trait name");
        assertEquals("PMSevLeaf", abbreviations.get(0), "wrong abbreviation");
        assertEquals("PM_LEAF_P4", abbreviations.get(1), "wrong abbreviation");
        assertEquals("Powdery Mildew", synonyms.get(0), "wrong synonym");
        assertEquals("Powdery Mildew Severity", synonyms.get(1), "wrong synonym");
        assertEquals("Powdery mildew (PM) due to Erysiphe necator severity in field, leaves only", trait.getDescription(), "wrong description");
        assertEquals("Plant", trait.getProgramObservationLevel().getName(), "wrong level name");
        assertEquals(true, trait.getActive(), "wrong status");
        // TODO: trait lists
        Method method = trait.getMethod();
        assertEquals("Powdery Mildew severity, leaves - Estimation", method.getMethodName(), "wrong method name");
        assertEquals("Observed severity of Powdery Mildew on leaves", method.getDescription(), "wrong method description");
        assertEquals("Estimation", method.getMethodClass(), "wrong method class");
        assertEquals("a^2 + b^2 = c^2", method.getFormula(), "wrong method formula");
        Scale scale = trait.getScale();
        assertEquals("1-4 Parlier field response score", scale.getScaleName(), "wrong scale name");
        assertEquals(DataType.ORDINAL, scale.getDataType(), "wrong scale dataType");
        assertEquals(2, scale.getDecimalPlaces(), "wrong scale decimal places");
        assertEquals(2, scale.getValidValueMin(), "wrong scale min value");
        assertEquals(9999, scale.getValidValueMax(), "wrong scale max value");
    }

    // didn't do tests for file with multiple rows for all data, just number of traits to save time

    @Test
    @SneakyThrows
    void parseCsvMultipleRowsSuccess() {
        File file = new File("src/test/resources/files/data_multiple_rows.csv");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseCsv(inputStream);

        assertEquals(3, traits.size(), "number of traits different than expected");
    }

    @Test
    @SneakyThrows
    void parseXlsMultipleRowsSuccess() {
        File file = new File("src/test/resources/files/data_multiple_rows.xls");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseExcel(inputStream);

        assertEquals(3, traits.size(), "number of traits different than expected");
    }

    @Test
    @SneakyThrows
    void parseXlsxMultipleRowsSuccess() {
        File file = new File("src/test/resources/files/data_multiple_rows.xlsx");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseExcel(inputStream);

        assertEquals(3, traits.size(), "number of traits different than expected");
    }

}
