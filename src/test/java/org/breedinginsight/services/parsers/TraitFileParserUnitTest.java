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

import junit.framework.AssertionFailedError;
import lombok.SneakyThrows;
import org.breedinginsight.api.model.v1.response.RowValidationErrors;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.enums.TermType;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.parsers.trait.TraitFileColumns;
import org.breedinginsight.services.parsers.trait.TraitFileParser;
import org.breedinginsight.services.validators.TraitFileValidatorError;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mock;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TraitFileParserUnitTest {

    private TraitFileParser parser;
    @Mock
    TraitFileParser mockParser;

    @BeforeAll
    void setup() {
        parser = new TraitFileParser(new TraitFileValidatorError());
    }

    @Test
    @SneakyThrows
    void parseCsvMissingColumnData() {
        File file = new File("src/test/resources/files/missing_trait_name_with_data.csv");
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
        assertEquals(ParsingExceptionType.MISSING_COLUMN_NAMES_ROW, e.getType(), "Wrong type");
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
    void parseCsvConvertToTraitsError() {

        File file = new File("src/test/resources/files/multiple_rows_parsing_errors.csv");
        InputStream inputStream = new FileInputStream(file);

        ValidatorException e = assertThrows(ValidatorException.class, () -> parser.parseCsv(inputStream), "expected parsing exception");

        ValidationErrors rowErrors = e.getErrors();
        assertTrue(rowErrors.getRowErrors().size() == 3, "Wrong number of row errors returned");

        Map<String, ParsingExceptionType> expectedErrors1 = new HashMap<>();
        expectedErrors1.put(TraitFileColumns.SCALE_CLASS.toString(), ParsingExceptionType.INVALID_SCALE_CLASS);
        expectedErrors1.put(TraitFileColumns.STATUS.toString(), ParsingExceptionType.INVALID_TRAIT_STATUS);

        Map<String, ParsingExceptionType> expectedErrors2 = new HashMap<>();
        expectedErrors2.putAll(expectedErrors1);
        expectedErrors2.put(TraitFileColumns.SCALE_CATEGORIES.toString(), ParsingExceptionType.INVALID_SCALE_CATEGORIES);
        Map<String, ParsingExceptionType> expectedErrors3 = new HashMap<>();
        expectedErrors3.putAll(expectedErrors1);
        expectedErrors3.put(TraitFileColumns.SCALE_DECIMAL_PLACES.toString(), ParsingExceptionType.INVALID_SCALE_DECIMAL_PLACES);
        expectedErrors3.put(TraitFileColumns.SCALE_LOWER_LIMIT.toString(), ParsingExceptionType.INVALID_SCALE_LOWER_LIMIT);
        expectedErrors3.put(TraitFileColumns.SCALE_UPPER_LIMIT.toString(), ParsingExceptionType.INVALID_SCALE_UPPER_LIMIT);
        expectedErrors3.put(TraitFileColumns.TERM_TYPE.toString(), ParsingExceptionType.INVALID_TERM_TYPE);

        checkParsingExceptionErrors(rowErrors.getRowErrors().get(0), expectedErrors1);
        checkParsingExceptionErrors(rowErrors.getRowErrors().get(1), expectedErrors2);
        checkParsingExceptionErrors(rowErrors.getRowErrors().get(2), expectedErrors3);
    }

    void checkParsingExceptionErrors(RowValidationErrors rowError, Map<String, ParsingExceptionType> errorColumns) {

        Boolean unknownExceptionReturned = false;
        Map<String,Boolean> seenMap = new HashMap<>();
        errorColumns.keySet().forEach(e->seenMap.put(e,false));

        for (ValidationError error: rowError.getErrors()){
            if (errorColumns.containsKey(error.getField())){
                ParsingExceptionType exceptionType = errorColumns.get(error.getField());
                assertEquals(422, error.getHttpStatusCode(), "Wrong status code");
                assertEquals(exceptionType.toString(), error.getErrorMessage(), "Wrong error message");
                seenMap.replace(error.getField(), true);
            } else {
                unknownExceptionReturned = true;
            }
        }

        if (unknownExceptionReturned){
            throw new AssertionFailedError("Unknown exception was returned");
        }

        if (seenMap.values().contains(false)){
            throw new AssertionFailedError("Not all exceptions were returned");
        }

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
        List<String> synonyms = trait.getSynonyms();
        assertEquals(2, synonyms.size(), "number of synonyms different than expected");

        assertEquals("PM_Leaf", trait.getObservationVariableName(), "wrong trait name");
        assertEquals("Powdery Mildew", synonyms.get(0), "wrong synonym");
        assertEquals("Powdery Mildew Severity", synonyms.get(1), "wrong synonym");
        assertEquals("leaf", trait.getEntity(), "wrong entity");
        assertEquals("powdery mildew severity", trait.getAttribute(), "wrong attribute");
        assertEquals(true, trait.getActive(), "wrong status");
        assertEquals(TermType.GERM_PASSPORT, trait.getTermType(), "wrong term type");
        // TODO: trait lists
        Method method = trait.getMethod();
        assertEquals("Powdery Mildew severity, leaf", method.getDescription(), "wrong method description");
        assertEquals("Estimation", method.getMethodClass(), "wrong method class");
        assertEquals("a^2 + b^2 = c^2", method.getFormula(), "wrong method formula");
        Scale scale = trait.getScale();
        assertEquals("Ordinal", scale.getScaleName(), "wrong scale name");
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

    @Test
    @SneakyThrows
    void parseCsvNullRowsSuccess() {
        File file = new File("src/test/resources/files/ontology/ontology_null_rows.csv");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseCsv(inputStream);

        assertEquals(3, traits.size(), "number of traits different than expected");
    }

    @Test
    @SneakyThrows
    void parseXlsNullRowsSuccess() {
        File file = new File("src/test/resources/files/ontology/ontology_null_rows.xls");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseExcel(inputStream);

        assertEquals(3, traits.size(), "number of traits different than expected");
    }

    @Test
    @SneakyThrows
    void parseXlsxNullRowsSuccess() {
        File file = new File("src/test/resources/files/ontology/ontology_null_rows.xlsx");
        InputStream inputStream = new FileInputStream(file);
        List<Trait> traits = parser.parseExcel(inputStream);

        assertEquals(3, traits.size(), "number of traits different than expected");
    }
}
