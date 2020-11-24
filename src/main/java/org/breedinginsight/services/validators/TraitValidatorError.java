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

package org.breedinginsight.services.validators;

import io.micronaut.http.HttpStatus;
import org.breedinginsight.api.model.v1.response.ValidationError;

import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class TraitValidatorError implements TraitValidatorErrorInterface {

    @Override
    public Integer getRowNumber(Integer row) {
        // 0 indexed row
        return row;
    }

    @Override
    public ValidationError getMissingMethodMsg() {
        return new ValidationError("method", "Missing method", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingMethodDescriptionMsg() {
        return new ValidationError("method.description", "Missing method description", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingMethodClassMsg() {
        return new ValidationError("method.methodClass", "Missing method class", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingScaleMsg() {
        return new ValidationError("scale", "Missing scale", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingScaleNameMsg() {
        return new ValidationError("scale.scaleName", "Missing scale name", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingScaleDataTypeMsg() {
        return new ValidationError("scale.dataType", "Missing scale data type", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingTraitNameMsg() {
        return new ValidationError("traitName", "Missing trait name", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingProgramObservationLevelMsg() {
        return new ValidationError("programObservationLevel.name", "Missing program observation level", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingMethodFormulaMsg() {
        return new ValidationError("method.formula", "Missing method formula for Computation method", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingScaleCategoriesMsg() {
        return new ValidationError("scale.categories", "Missing scale categories for Ordinal scale", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getDuplicateTraitByNamesMsg() {
        return new ValidationError("traitName", "Trait name - Scale name combination already exists", HttpStatus.CONFLICT);
    }

    @Override
    public ValidationError getDuplicateTraitByAbbreviationsMsg() {
        return new ValidationError("abbreviations", "Trait abbreviation already exists", HttpStatus.CONFLICT);
    }

    @Override
    public ValidationError getDuplicateTraitsByNameInFileMsg(List<Integer> matchingRows) {
        matchingRows = matchingRows.stream().map(rowIndex -> getRowNumber(rowIndex)).collect(Collectors.toList());
        return new ValidationError("traitName",
                "traitName - scaleName combination is a duplicate. Duplicate set of traits are rows " + matchingRows.toString(),
                HttpStatus.CONFLICT);
    }

    @Override
    public ValidationError getDuplicateTraitsByAbbreviationInFileMsg(List<Integer> matchingRows) {
        matchingRows = matchingRows.stream().map(rowIndex -> getRowNumber(rowIndex)).collect(Collectors.toList());
        return new ValidationError("abbreviations",
                "One or more abbreviations is a duplicate of abbreviations. Set of traits with these matching abbreviations found in rows " + matchingRows.toString(),
                HttpStatus.CONFLICT);
    }

    @Override
    public ValidationError getTraitLevelDoesNotExist(List<String> availableTraitLevels) {
        return new ValidationError("programObservationLevel.name",
                "Program Observation Level does not exist. Available levels are " + availableTraitLevels.toString(), HttpStatus.NOT_FOUND);
    }
}
