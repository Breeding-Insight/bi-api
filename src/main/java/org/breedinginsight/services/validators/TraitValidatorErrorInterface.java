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

import org.breedinginsight.api.model.v1.response.ValidationError;

import java.util.List;

public interface TraitValidatorErrorInterface {
    Integer getRowNumber(Integer row);
    ValidationError getTraitIdDoesNotExistMsg();
    ValidationError getMissingMethodMsg();
    ValidationError getMissingMethodDescriptionMsg();
    ValidationError getMissingMethodClassMsg();
    ValidationError getMissingScaleMsg();
    ValidationError getMissingScaleNameMsg();
    ValidationError getMissingScaleDataTypeMsg();
    ValidationError getMissingTraitNameMsg();
    ValidationError getMissingProgramObservationLevelMsg();
    ValidationError getMissingMethodFormulaMsg();
    ValidationError getMissingScaleCategoriesMsg();
    ValidationError getBadScaleCategory();
    ValidationError getBlankScaleCategoryLabelMsg();
    ValidationError getBlankScaleCategoryValueMsg();
    ValidationError getMaxLessThenMinError();
    ValidationError getDuplicateTraitByNamesMsg();
    ValidationError getDuplicateTraitByAbbreviationsMsg();
    ValidationError getDuplicateTraitsByNameInFileMsg(List<Integer> matchingRows);
    ValidationError getDuplicateTraitsByAbbreviationInFileMsg(List<Integer> matchingRows);
}
