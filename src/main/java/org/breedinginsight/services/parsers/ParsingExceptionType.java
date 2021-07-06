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

public enum ParsingExceptionType {

    MISSING_COLUMN_NAMES("Missing column names row"),
    COLUMN_NAME_NOT_STRING("Column name must be string cell"),
    DUPLICATE_COLUMN_NAMES("Found duplicate column names"),
    MISSING_EXPECTED_COLUMNS("Missing expected columns"),
    ERROR_READING_FILE("Error reading file"),
    MISSING_SHEET("Missing sheet Template"),
    EMPTY_ROW("Empty row"),
    INVALID_TRAIT_STATUS("Invalid trait status value"),
    INVALID_SCALE_CLASS("Invalid scale class value"),
    MISSING_SCALE_CLASS("Missing scale class value"),
    INVALID_SCALE_DECIMAL_PLACES("Invalid scale decimal places value"),
    INVALID_SCALE_LOWER_LIMIT("Invalid scale lower limit value. Value must be numeric and be a whole number."),
    INVALID_SCALE_UPPER_LIMIT("Invalid scale upper limit value. Value must be numeric and be a whole number."),
    INVALID_SCALE_CATEGORIES("Invalid scale categories format");

    private String value;

    ParsingExceptionType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public String getMessage() {
        return value;
    }

}
