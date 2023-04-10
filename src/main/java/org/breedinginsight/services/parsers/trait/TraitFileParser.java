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

import io.micronaut.http.HttpStatus;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.text.WordUtils;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.model.imports.TermTypeTranslator;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.enums.TermType;
import org.breedinginsight.model.*;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.ParsingExceptionType;
import org.breedinginsight.services.parsers.excel.ExcelParser;
import org.breedinginsight.services.parsers.excel.ExcelRecord;
import org.breedinginsight.services.validators.TraitFileValidatorError;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static org.jooq.tools.StringUtils.isBlank;


// can read file, columns with set of allowable values checked or requirement of particular data format
// data consistency not checked, must be done by caller
@Slf4j
@Singleton
public class TraitFileParser {

    private static final String LIST_DELIMITER = ";";
    private static final String CATEGORY_DELIMITER = "=";
    private static final String EXCEL_DATA_SHEET_NAME = "Data";
    private static final String OLD_EXCEL_DATA_SHEET_NAME = "Template";


    private static final String TRAIT_STATUS_ACTIVE = "active";
    private static final String TRAIT_STATUS_ARCHIVED = "archived";

    private final static Set TRAIT_STATUS_VALID_VALUES = Collections.unmodifiableSet(
            Set.of(TRAIT_STATUS_ACTIVE, TRAIT_STATUS_ARCHIVED));

    private TraitFileValidatorError traitValidatorError;

    @Inject
    public TraitFileParser(TraitFileValidatorError traitValidatorError){
        this.traitValidatorError = traitValidatorError;
    }

    public List<Trait> parseExcel(@NonNull InputStream inputStream) throws ParsingException, ValidatorException {

        Workbook workbook = null;
        try {
            workbook = WorkbookFactory.create(inputStream);
        } catch (IOException | EncryptedDocumentException e) {
            log.error(e.getMessage());
            throw new ParsingException(ParsingExceptionType.ERROR_READING_FILE);
        }
        Sheet sheet = workbook.getSheet(EXCEL_DATA_SHEET_NAME);

        // accept the old sheet name ("template") for backwards compatability
        if (sheet == null){
            sheet = workbook.getSheet(OLD_EXCEL_DATA_SHEET_NAME);
        }

        if (sheet == null) {
            throw new ParsingException(ParsingExceptionType.MISSING_SHEET);
        }

        List<ExcelRecord> records = ExcelParser.parse(sheet, TraitFileColumns.getColumns());

        return excelRecordsToTraits(records);
    }

    // no sheets RFC4180
    public List<Trait> parseCsv(@NonNull InputStream inputStream) throws ParsingException, ValidatorException {

        ArrayList<Trait> traits = new ArrayList<>();
        InputStreamReader in = new InputStreamReader(inputStream);

        Iterable<CSVRecord> records = null;
        try {
            // withHeader for enum uses name() internally so we have to give string array instead
            records = CSVFormat.DEFAULT
                    .parse(in);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new ParsingException(ParsingExceptionType.ERROR_READING_FILE);
        }

        Sheet excelSheet = convertCsvToExcel(records);
        List<ExcelRecord> excelRecords = ExcelParser.parse(excelSheet, TraitFileColumns.getColumns());

        return excelRecordsToTraits(excelRecords);
    }

    private List<Trait> excelRecordsToTraits(List<ExcelRecord> records) throws ValidatorException {
        List<Trait> traits = new ArrayList<>();
        ValidationErrors validationErrors = new ValidationErrors();

        for (int i = 0; i < records.size(); i++) {

            ExcelRecord record = records.get(i);

            ProgramObservationLevel level = ProgramObservationLevel.builder()
                    .name(parseExcelValueAsString(record, TraitFileColumns.TRAIT_ENTITY))
                    .build();


            Boolean active;
            String traitStatus = parseExcelValueAsString(record, TraitFileColumns.STATUS);
            if (traitStatus == null) {
                active = true;
            } else {
                if (!TRAIT_STATUS_VALID_VALUES.contains(traitStatus.toLowerCase())) {
                    ValidationError error = new ValidationError(TraitFileColumns.STATUS.toString(),
                            ParsingExceptionType.INVALID_TRAIT_STATUS.toString(), HttpStatus.UNPROCESSABLE_ENTITY);
                    validationErrors.addError(traitValidatorError.getRowNumber(i), error);
                }
                active = !traitStatus.toLowerCase().equals(TRAIT_STATUS_ARCHIVED);
            }

            // Normalize and capitalize method class
            String methodClassStr = parseExcelValueAsString(record, TraitFileColumns.METHOD_CLASS);
            MethodClass methodClass = null;

            if (methodClassStr != null) {
                try {
                    methodClass = MethodClass.valueOf(methodClassStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.error(e.getMessage());
                    ValidationError error = new ValidationError(TraitFileColumns.METHOD_CLASS.toString(),
                            ParsingExceptionType.INVALID_METHOD_CLASS.toString(), HttpStatus.UNPROCESSABLE_ENTITY);
                    validationErrors.addError(traitValidatorError.getRowNumber(i), error);
                }
            }

            Method method = Method.builder()
                    .description(parseExcelValueAsString(record, TraitFileColumns.METHOD_DESCRIPTION))
                    .methodClass(methodClass != null ? methodClass.getLiteral() : null)
                    .formula(parseExcelValueAsString(record, TraitFileColumns.METHOD_FORMULA))
                    .build();

            DataType dataType = null;
            String dataTypeString = parseExcelValueAsString(record, TraitFileColumns.SCALE_CLASS);

            if (dataTypeString != null) {
                try {
                    dataType = DataType.valueOf(dataTypeString.toUpperCase());

                    //This if statement can be removed once DURATION is no longer a valid Data Type
                    if( DataType.DURATION == dataType ){
                        throw new IllegalArgumentException("DURATION is not a valid datatype for batch uploading");
                    }

                } catch (IllegalArgumentException e) {
                    log.error(e.getMessage());
                    ValidationError error = new ValidationError(TraitFileColumns.SCALE_CLASS.toString(),
                            ParsingExceptionType.INVALID_SCALE_CLASS.toString(), HttpStatus.UNPROCESSABLE_ENTITY);
                    validationErrors.addError(traitValidatorError.getRowNumber(i), error);
                }
            }

            List<BrAPIScaleValidValuesCategories> categories = new ArrayList<>();
            String categoriesString = parseExcelValueAsString(record, TraitFileColumns.SCALE_CATEGORIES);
            List<String> categoriesStringList = parseListValue(categoriesString);
            try {
                for (String value: categoriesStringList){
                    categories.add(parseCategory(value));
                }
            } catch (UnprocessableEntityException e){
                log.error(e.getMessage());
                ValidationError error = new ValidationError(TraitFileColumns.SCALE_CATEGORIES.toString(),
                        ParsingExceptionType.INVALID_SCALE_CATEGORIES.toString(), HttpStatus.UNPROCESSABLE_ENTITY);
                validationErrors.addError(traitValidatorError.getRowNumber(i), error);
            }

            Integer decimalPlaces = null;
            Integer validValueMin = null;
            Integer validValueMax = null;
            String decimalPlacesStr = parseExcelValueAsString(record, TraitFileColumns.SCALE_DECIMAL_PLACES);
            String validValueMinStr = parseExcelValueAsString(record, TraitFileColumns.SCALE_LOWER_LIMIT);
            String validValueMaxStr = parseExcelValueAsString(record, TraitFileColumns.SCALE_UPPER_LIMIT);

            // allow null since field can be blank
            if (decimalPlacesStr != null) {
                try {
                    decimalPlaces = Integer.valueOf(decimalPlacesStr);
                } catch (NumberFormatException e) {
                    log.error(e.getMessage());
                    ValidationError error = new ValidationError(TraitFileColumns.SCALE_DECIMAL_PLACES.toString(),
                            ParsingExceptionType.INVALID_SCALE_DECIMAL_PLACES.toString(), HttpStatus.UNPROCESSABLE_ENTITY);
                    validationErrors.addError(traitValidatorError.getRowNumber(i), error);
                }
            }

            if (validValueMinStr != null) {
                try {
                    validValueMin = Integer.valueOf(validValueMinStr);
                } catch (NumberFormatException e) {
                    log.error(e.getMessage());
                    ValidationError error = new ValidationError(TraitFileColumns.SCALE_LOWER_LIMIT.toString(),
                            ParsingExceptionType.INVALID_SCALE_LOWER_LIMIT.toString(), HttpStatus.UNPROCESSABLE_ENTITY);
                    validationErrors.addError(traitValidatorError.getRowNumber(i), error);
                }
            }

            if (validValueMaxStr != null) {
                try {
                    validValueMax = Integer.valueOf(validValueMaxStr);
                } catch (NumberFormatException e) {
                    log.error(e.getMessage());
                    ValidationError error = new ValidationError(TraitFileColumns.SCALE_UPPER_LIMIT.toString(),
                            ParsingExceptionType.INVALID_SCALE_UPPER_LIMIT.toString(), HttpStatus.UNPROCESSABLE_ENTITY);
                    validationErrors.addError(traitValidatorError.getRowNumber(i), error);
                }
            }

            Scale scale = Scale.builder()
                    .scaleName(parseExcelValueAsString(record, TraitFileColumns.SCALE_NAME))
                    .dataType(dataType)
                    .decimalPlaces(decimalPlaces)
                    .validValueMin(validValueMin)
                    .validValueMax(validValueMax)
                    .categories(categories)
                    .build();

            String synonymsString = parseExcelValueAsString(record, TraitFileColumns.SYNONYMS);
            List<String> traitSynonyms = parseListValue(synonymsString);

            String tagsString = parseExcelValueAsString(record, TraitFileColumns.TAGS);
            List<String> traitTags = parseListValue(tagsString);

            //Set to backend value for user input term type, if none, set to default
            TermType termType = TermType.PHENOTYPE;

            String termTypeVal = parseExcelValueAsString(record, TraitFileColumns.TERM_TYPE);
            if ((!Objects.isNull(termTypeVal)) && (!termTypeVal.isBlank())) {
                Optional<TermType> termTypeOpt = TermTypeTranslator.getTermTypeFromUserDisplayName(WordUtils.capitalizeFully(termTypeVal));
                if (termTypeOpt.isPresent()) {
                    termType = termTypeOpt.get();
                } else {
                    ValidationError error = new ValidationError(TraitFileColumns.TERM_TYPE.toString(),
                            ParsingExceptionType.INVALID_TERM_TYPE.toString(), HttpStatus.UNPROCESSABLE_ENTITY);
                    validationErrors.addError(traitValidatorError.getRowNumber(i), error);
                }
            }

            Trait trait = Trait.builder()
                    .observationVariableName(parseExcelValueAsString(record, TraitFileColumns.NAME))
                    .traitDescription(parseExcelValueAsString(record, TraitFileColumns.DESCRIPTION))
                    .entity(parseExcelValueAsString(record, TraitFileColumns.TRAIT_ENTITY))
                    .attribute(parseExcelValueAsString(record, TraitFileColumns.TRAIT_ATTRIBUTE))
                    .synonyms(traitSynonyms)
                    .programObservationLevel(level)
                    .active(active)
                    .fullName(parseExcelValueAsString(record, TraitFileColumns.FULL_NAME))
                    // TODO: trait lists
                    .method(method)
                    .scale(scale)
                    .tags(traitTags)
                    .termType(termType)
                    .build();

            traits.add(trait);

        }

        if (validationErrors.getRowErrors().size() > 0){
            throw new ValidatorException(validationErrors);
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
                .filter(s -> !isBlank(s))
                .collect(Collectors.toList());
    }

    private BrAPIScaleValidValuesCategories parseCategory(String value) throws UnprocessableEntityException {

        BrAPIScaleValidValuesCategories category = new BrAPIScaleValidValuesCategories();

        String[] labelMeaning = value.split(CATEGORY_DELIMITER);
        if (labelMeaning.length == 2) {
            category.setValue(labelMeaning[0].trim());
            category.setLabel(labelMeaning[1].trim());
        }
        else if (labelMeaning.length == 1) {
            category.setValue(labelMeaning[0].trim());
        } else if (labelMeaning.length > 2){
            // The case where there are multiple category delimiters in a value. Could be cause by bad list delimeter.
            throw new UnprocessableEntityException("Unable to parse categories");
        }

        return category;
    }

}

