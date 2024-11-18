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
public class TraitFileValidatorError implements TraitValidatorErrorInterface {

    @Override
    public Integer getRowNumber(Integer row) {
        // 1 indexed row, we skip the first for the header
        return row + 2;
    }

    @Override
    public ValidationError getTraitIdDoesNotExistMsg() {
        return new ValidationError("Trait Id", "Missing trait id", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingMethodMsg() {
        return new ValidationError("Method", "Missing method", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingMethodClassMsg() {
        return new ValidationError("Method Class", "Missing method class", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingScaleMsg() {
        return new ValidationError("Scale", "Missing scale class", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingScaleUnitMsg() {
        return new ValidationError("Unit", "Missing unit", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingScaleDataTypeMsg() {
        return new ValidationError("Scale Class", "Missing scale class", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getInvalidCharObsVarNameMsg() {
        return new ValidationError("Name", "Periods and brackets are invalid", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingObsVarNameMsg() {
        return new ValidationError("Name", "Missing name", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingTraitEntityMsg() {
        return new ValidationError("Trait Entity", "Missing trait entity", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingTraitAttributeMsg() {
        return new ValidationError("Trait Attribute", "Missing trait attribute", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingTraitDescriptionMsg() {
        return new ValidationError("Trait Description", "Missing trait description", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingProgramObservationLevelMsg() {
        return new ValidationError("Trait Level", "Missing trait level", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingMethodFormulaMsg() {
        return new ValidationError("Method Formula", "Missing method formula for Computation method", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingScaleCategoriesMsg(DataType dataType) {
        return new ValidationError("Scale Categories",
                String.format("Missing scale categories for %s scale", WordUtils.capitalize(dataType.getLiteral().toLowerCase())),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getBadScaleCategory() {
        return new ValidationError("Scale Categories", "Scale categories contain errors", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getBlankScaleCategoryLabelMsg() {
        return new ValidationError("Scale Categories",
                "Scale category label cannot be blank.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getBlankScaleCategoryValueMsg() {
        return new ValidationError("Scale Categories",
                "Scale category value cannot be blank.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getPopulatedNominalCategoryLabelMsg() {
        return new ValidationError("Scale Categories",
                "Scale label cannot be populated for Nominal scale type",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMaxLessThenMinError() {
        return new ValidationError("Scale upper limit/Scale lower limit",
                "Scale upper limit must be greater than scale lower limit.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getInsufficientNominalValError() {
        return new ValidationError("Scale Categories", "Nominal scales must have at least one category.", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getInsufficientOrdinalValError() {
        return new ValidationError("Scale Categories", "Ordinal scales must have at least two categories.", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getCharLimitObsVarNameMsg() {
        return new ValidationError("Name", "Name exceeds 16 character limit", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getCharLimitTraitEntityMsg() {
        return new ValidationError("Entity", "Trait entity exceeds 30 character limit", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getCharLimitTraitAttributeMsg() {
        return new ValidationError("Attribute", "Trait attribute exceeds 30 character limit", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getCharLimitMethodDescriptionMsg() {
        return new ValidationError("Method Description", "Method description exceeds 30 character limit", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getDuplicateTraitByNamesMsg() {
        return new ValidationError("Trait Name", "Trait name already exists", HttpStatus.CONFLICT);
    }
    
    @Override
    public ValidationError getDuplicateTraitsByNameInFileMsg(List<Integer> matchingRows) {
        matchingRows = matchingRows.stream().map(rowIndex -> getRowNumber(rowIndex)).collect(Collectors.toList());
        return new ValidationError("Trait Name",
                "Trait name duplicated in file. Duplicate set of traits are rows " + matchingRows.toString(),
                HttpStatus.CONFLICT);
    }

}
