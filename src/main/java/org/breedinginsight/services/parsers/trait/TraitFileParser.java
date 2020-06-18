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
import org.brapi.v2.phenotyping.model.BrApiScaleCategories;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.ProgramObservationLevel;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.parsers.ParsingException;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


// can read file, columns with set of allowable values checked or requirement of particular data format
// data consistency not checked, must be done by caller
@Slf4j
public class TraitFileParser {

    private static final String LIST_DELIMITER = ";";
    private static final String CATEGORY_DELIMITER = "=";
    private static final String TRAIT_STATUS_ARCHIVED = "archived";

    // no sheets RFC4180
    public List<Trait> parseCsv(@NonNull InputStream inputStream) throws ParsingException {

        ArrayList<Trait> traits = new ArrayList<>();
        InputStreamReader in = new InputStreamReader(inputStream);

        Iterable<CSVRecord> records = null;
        try {
            // withHeader for enum uses name() internally so we have to give string array instead
            records = CSVFormat.DEFAULT
                    .withHeader(TraitFileColumns.getColumns())
                    .withFirstRecordAsHeader()
                    .parse(in);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new ParsingException("Error reading file");
        }

        for (CSVRecord record : records) {

            ProgramObservationLevel level = ProgramObservationLevel.builder()
                    .name(parseValue(record, TraitFileColumns.TRAIT_LEVEL))
                    .build();

            // TODO: throw if not active/archived/empty

            Boolean active = !parseValue(record, TraitFileColumns.TRAIT_STATUS).equals(TRAIT_STATUS_ARCHIVED);

            Method method = Method.builder()
                    .methodName(parseValue(record, TraitFileColumns.METHOD_NAME))
                    .description(parseValue(record, TraitFileColumns.METHOD_DESCRIPTION))
                    .methodClass(parseValue(record, TraitFileColumns.METHOD_CLASS))
                    .formula(parseValue(record, TraitFileColumns.METHOD_FORMULA))
                    .build();

            // TODO: throw if not valid datatype
            // null check?
            DataType dataType = null;

            try {
                dataType = DataType.valueOf(parseValue(record, TraitFileColumns.SCALE_CLASS).toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error(e.getMessage());
            }

            // TODO: throw if bad format
            List<BrApiScaleCategories> categories = parseListValue(record.get(TraitFileColumns.SCALE_CATEGORIES)).stream()
                    .map(value -> parseCategory(value))
                    .collect(Collectors.toList());

            Integer decimalPlaces = null;
            Integer validValueMin = null;
            Integer validValueMax = null;

            try {
                decimalPlaces = Integer.valueOf(parseValue(record, TraitFileColumns.SCALE_DECIMAL_PLACES));
                validValueMin = Integer.valueOf(parseValue(record, TraitFileColumns.SCALE_LOWER_LIMIT));
                validValueMax = Integer.valueOf(parseValue(record, TraitFileColumns.SCALE_UPPER_LIMIT));
            } catch (NumberFormatException e) {
                log.info(e.getMessage());
            }

            Scale scale = Scale.builder()
                    .scaleName(parseValue(record, TraitFileColumns.SCALE_NAME))
                    .dataType(dataType)
                    .decimalPlaces(decimalPlaces)
                    .validValueMin(validValueMin)
                    .validValueMax(validValueMax)
                    .categories(categories)
                    .build();

            Trait trait = Trait.builder()
                    .traitName(parseValue(record, TraitFileColumns.TRAIT_NAME))
                    .abbreviations(parseListValue(record.get(TraitFileColumns.TRAIT_ABBREVIATIONS)))
                    .synonyms(parseListValue(record.get(TraitFileColumns.TRAIT_SYNONYMS)))
                    .description(parseValue(record, TraitFileColumns.TRAIT_DESCRIPTION))
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

    private String parseValue(CSVRecord record, TraitFileColumns column) throws ParsingException {
        String value = null;

        try {
            value =  record.get(column).trim();
        } catch(IllegalArgumentException | IllegalStateException e) {
            log.error(e.getMessage());
            throw new ParsingException("Error reading value");
        }

        return value;
    }

    private List<String> parseListValue(String value) {
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
