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
import org.apache.commons.text.WordUtils;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.dao.db.enums.DataType;

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
    public ValidationError getTraitIdDoesNotExistMsg() {
        return new ValidationError("traitId", "Trait with that id does not exist", HttpStatus.NOT_FOUND);
    }

    @Override
    public ValidationError getMissingMethodMsg() {
        return new ValidationError("method", "Missing method", HttpStatus.BAD_REQUEST);
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
    public ValidationError getMissingObsVarNameMsg() {
        return new ValidationError("observationVariableName", "Missing observation variable name", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingTraitEntityMsg() {
        return new ValidationError("entity", "Missing trait entity", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingTraitAttributeMsg() {
        return new ValidationError("attribute", "Missing trait attribute", HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getMissingTraitDescriptionMsg() {
        return new ValidationError("traitDescription", "Missing trait description", HttpStatus.BAD_REQUEST);
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
    public ValidationError getMissingScaleCategoriesMsg(DataType dataType) {
        return new ValidationError("scale.categories",
                String.format("Missing scale categories for %s scale", WordUtils.capitalize(dataType.getLiteral().toLowerCase())),
                HttpStatus.BAD_REQUEST);
    }

    @Override
    public ValidationError getBadScaleCategory() {
        return new ValidationError("scale.categories", "Scale categories contain errors", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getBlankScaleCategoryLabelMsg() {
        return new ValidationError("scale.categories.label",
                "Label missing.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getBlankScaleCategoryValueMsg() {
        return new ValidationError("scale.categories.value",
                "Value missing.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getPopulatedNominalCategoryLabelMsg() {
        return new ValidationError("scale.categories.label",
                "Scale label cannot be populated for Nominal scale type",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMaxLessThenMinError() {
        return new ValidationError("scale.validValueMax",
                "Scale valid value max must be greater than valid value min.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getInsufficientNominalValError() {
        return new ValidationError("scale.categories", "Nominal scales must have at least one category.", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getInsufficientOrdinalValError() {
        return new ValidationError("scale.categories", "Ordinal scales must have at least two categories.", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getCharLimitObsVarNameMsg() {
        return new ValidationError("observationVariableName", "Observation variable name exceeds 12 character limit", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getCharLimitTraitEntityMsg() {
        return new ValidationError("entity", "Trait entity exceeds 30 character limit", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getCharLimitTraitAttributeMsg() {
        return new ValidationError("attribute", "Trait attribute exceeds 30 character limit", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getCharLimitMethodDescriptionMsg() {
        return new ValidationError("method.description", "Method description exceeds 30 character limit", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getDuplicateTraitByNamesMsg() {
        return new ValidationError("traitName", "Trait name already exists", HttpStatus.CONFLICT);
    }


    @Override
    public ValidationError getDuplicateTraitsByNameInFileMsg(List<Integer> matchingRows) {
        matchingRows = matchingRows.stream().map(rowIndex -> getRowNumber(rowIndex)).collect(Collectors.toList());
        return new ValidationError("traitName",
                "traitName is a duplicate. Duplicate set of traits are rows " + matchingRows.toString(),
                HttpStatus.CONFLICT);
    }

}
