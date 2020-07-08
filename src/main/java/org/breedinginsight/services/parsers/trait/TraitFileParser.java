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
package org.breedinginsight.services.parsers.trait;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;

import org.brapi.v2.phenotyping.model.BrApiScaleCategories;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.ProgramObservationLevel;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.parsers.excel.ExcelParser;
import org.breedinginsight.services.parsers.excel.ExcelRecord;
import org.breedinginsight.services.parsers.ParsingException;

import java.io.*;
import java.util.*;

import javax.inject.Singleton;

import java.util.stream.Collectors;


// can read file, columns with set of allowable values checked or requirement of particular data format
// data consistency not checked, must be done by caller
@Slf4j
@Singleton
public class TraitFileParser {

    private static final String LIST_DELIMITER = ";";
    private static final String CATEGORY_DELIMITER = "=";
    private static final String EXCEL_DATA_SHEET_NAME = "Template";

    private static final String TRAIT_STATUS_ACTIVE = "active";
    private static final String TRAIT_STATUS_ARCHIVED = "archived";

    private final static Set TRAIT_STATUS_VALID_VALUES = Collections.unmodifiableSet(
            Set.of(TRAIT_STATUS_ACTIVE, TRAIT_STATUS_ARCHIVED));

    public List<Trait> parseExcel(@NonNull InputStream inputStream) throws ParsingException {

        Workbook workbook = null;
        try {
            workbook = WorkbookFactory.create(inputStream);
        } catch (IOException | EncryptedDocumentException e) {
            log.error(e.getMessage());
            throw new ParsingException("Error reading file");
        }

        Sheet sheet = workbook.getSheet(EXCEL_DATA_SHEET_NAME);
        if (sheet == null) {
            throw new ParsingException("Missing sheet" + EXCEL_DATA_SHEET_NAME);
        }

        List<ExcelRecord> records = ExcelParser.parse(sheet, TraitFileColumns.getColumns());
        return excelRecordsToTraits(records);
    }

    // no sheets RFC4180
    public List<Trait> parseCsv(@NonNull InputStream inputStream) throws ParsingException {

        ArrayList<Trait> traits = new ArrayList<>();
        InputStreamReader in = new InputStreamReader(inputStream);

        Iterable<CSVRecord> records = null;
        try {
            // withHeader for enum uses name() internally so we have to give string array instead
            records = CSVFormat.DEFAULT
                    .parse(in);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new ParsingException("Error reading file");
        }

        Sheet excelSheet = convertCsvToExcel(records);
        List<ExcelRecord> excelRecords = ExcelParser.parse(excelSheet, TraitFileColumns.getColumns());
        return excelRecordsToTraits(excelRecords);

    }

    private List<Trait> excelRecordsToTraits(List<ExcelRecord> records) throws ParsingException {
        List<Trait> traits = new ArrayList<>();

        for (ExcelRecord record : records) {

            ProgramObservationLevel level = ProgramObservationLevel.builder()
                    .name(parseExcelValueAsString(record, TraitFileColumns.TRAIT_LEVEL))
                    .build();

            Boolean active;
            String traitStatus = parseExcelValueAsString(record, TraitFileColumns.TRAIT_STATUS);
            if (traitStatus == null) {
                active = true;
            } else {
                if (!TRAIT_STATUS_VALID_VALUES.contains(traitStatus.toLowerCase())) {
                    throw new ParsingException("Invalid trait status value");
                }
                active = !traitStatus.equals(TRAIT_STATUS_ARCHIVED);
            }

            Method method = Method.builder()
                    .methodName(parseExcelValueAsString(record, TraitFileColumns.METHOD_NAME))
                    .description(parseExcelValueAsString(record, TraitFileColumns.METHOD_DESCRIPTION))
                    .methodClass(parseExcelValueAsString(record, TraitFileColumns.METHOD_CLASS))
                    .formula(parseExcelValueAsString(record, TraitFileColumns.METHOD_FORMULA))
                    .build();

            // will throw IllegalArgumentException
            DataType dataType = null;

            try {
                dataType = DataType.valueOf(parseExcelValueAsString(record, TraitFileColumns.SCALE_CLASS).toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error(e.getMessage());
            }

            // TODO: throw if bad format
            List<BrApiScaleCategories> categories = parseListValue(parseExcelValueAsString(record, TraitFileColumns.SCALE_CATEGORIES)).stream()
                    .map(value -> parseCategory(value))
                    .collect(Collectors.toList());

            Integer decimalPlaces = null;
            Integer validValueMin = null;
            Integer validValueMax = null;

            try {
                decimalPlaces = Integer.valueOf(parseExcelValueAsString(record, TraitFileColumns.SCALE_DECIMAL_PLACES));
                validValueMin = Integer.valueOf(parseExcelValueAsString(record, TraitFileColumns.SCALE_LOWER_LIMIT));
                validValueMax = Integer.valueOf(parseExcelValueAsString(record, TraitFileColumns.SCALE_UPPER_LIMIT));
            } catch (NumberFormatException e) {
                log.info(e.getMessage());
            }

            Scale scale = Scale.builder()
                    .scaleName(parseExcelValueAsString(record, TraitFileColumns.SCALE_NAME))
                    .dataType(dataType)
                    .decimalPlaces(decimalPlaces)
                    .validValueMin(validValueMin)
                    .validValueMax(validValueMax)
                    .categories(categories)
                    .build();

            Trait trait = Trait.builder()
                    .traitName(parseExcelValueAsString(record, TraitFileColumns.TRAIT_NAME))
                    .abbreviations(parseListValue(parseExcelValueAsString(record, TraitFileColumns.TRAIT_ABBREVIATIONS)))
                    .synonyms(parseListValue(parseExcelValueAsString(record, TraitFileColumns.TRAIT_SYNONYMS)))
                    .description(parseExcelValueAsString(record, TraitFileColumns.TRAIT_DESCRIPTION))
                    .programObservationLevel(level)
                    .active(active)
                    // TODO: trait lists
                    .method(method)
                    .scale(scale)
                    .build();

            traits.add(trait);

        }

        return traits;

    }

    private String parseExcelValueAsString(ExcelRecord record, TraitFileColumns column) {
        DataFormatter formatter = new DataFormatter();
        String value = formatter.formatCellValue(record.get(column)).trim(); // will return "" for nulls
        return value.equals("") ? null : value;
    }

    private Sheet convertCsvToExcel(Iterable<CSVRecord> records) {
        Workbook workbook = null;
        Sheet sheet = null;

        try {
            workbook = WorkbookFactory.create(false);
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        sheet = workbook.createSheet(EXCEL_DATA_SHEET_NAME);
        int rowIndex = 0;

        for (CSVRecord record : records) {
            Row row = sheet.createRow(rowIndex++);
            for (int colIndex=0; colIndex<record.size(); colIndex++) {
                row.createCell(colIndex).setCellValue(record.get(colIndex));
            }
        }

        return sheet;
    }

    private List<String> parseListValue(String value) {

        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(LIST_DELIMITER))
                .map(strVal -> strVal.trim())
                .collect(Collectors.toList());
    }

    private BrApiScaleCategories parseCategory(String value) {

        BrApiScaleCategories category = new BrApiScaleCategories();

        String[] labelMeaning = value.split(CATEGORY_DELIMITER);
        if (labelMeaning.length == 2) {
            category.setLabel(labelMeaning[0].trim());
            category.setValue(labelMeaning[1].trim());
        }
        else if (labelMeaning.length == 1) {
            category.setValue(labelMeaning[0].trim());
        }

        return category;
    }

}

