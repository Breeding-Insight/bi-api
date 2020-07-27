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

@Singleton
public class TraitFileValidatorError implements TraitValidatorErrorInterface {

    @Override
    public ValidationError getMissingMethodMsg() {
        return new ValidationError("method", "Missing method", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingMethodNameMsg() {
        return new ValidationError("Method name", "Missing method name", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingMethodDescriptionMsg() {
        return new ValidationError("Method description", "Missing method description", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingMethodClassMsg() {
        return new ValidationError("Method class", "Missing method class", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingScaleMsg() {
        return new ValidationError("scale", "Missing scale class", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingScaleNameMsg() {
        return new ValidationError("Scale name", "Missing scale name", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingScaleDataTypeMsg() {
        return new ValidationError("Scale class", "Missing scale class", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingTraitNameMsg() {
        return new ValidationError("Trait name", "Missing trait name", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingTraitDescriptionMsg() {
        return new ValidationError("Trait description", "Missing trait description", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingProgramObservationLevelMsg() {
        return new ValidationError("Trait level", "Missing trait level", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingMethodFormulaMsg() {
        return new ValidationError("Method formula", "Missing method formula for Computation method", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getMissingScaleCategoriesMsg() {
        return new ValidationError("Scale categories", "Missing scale categories for Ordinal scale", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Override
    public ValidationError getDuplicateTraitByNamesMsg() {
        return new ValidationError("Trait name", "Trait name - Scale name - Method name combination already exists", HttpStatus.CONFLICT);
    }

    @Override
    public ValidationError getDuplicateTraitByAbbreviationsMsg() {
        return new ValidationError("Trait abbreviations", "Trait abbreviation already exists", HttpStatus.CONFLICT);
    }

    @Override
    public ValidationError getDuplicateTraitsByNameInFileMsg(List<Integer> matchingRows) {
        return new ValidationError("Trait name",
                "traitName - scaleName - methodName combination is a duplicate. Duplicate set of traits are rows " + matchingRows.toString(),
                HttpStatus.CONFLICT);
    }

    @Override
    public ValidationError getDuplicateTraitsByAbbreviationInFileMsg(List<Integer> matchingRows) {
        return new ValidationError("Trait abbreviations",
                "One or more abbreviations is a duplicate of abbreviations. Set of traits with these matching abbreviations found in rows " + matchingRows.toString(),
                HttpStatus.CONFLICT);
    }

    @Override
    public ValidationError getTraitLevelDoesNotExist() {
        return new ValidationError("Trait level",
                "Trait level does not exist.", HttpStatus.NOT_FOUND);
    }
}
