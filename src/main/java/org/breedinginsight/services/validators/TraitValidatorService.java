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
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;

import javax.inject.Inject;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class TraitValidatorService {

    private TraitDAO traitDAO;

    @Inject
    public TraitValidatorService(TraitDAO traitDAO){
        this.traitDAO = traitDAO;
    }

    public ValidationErrors checkRequiredTraitFields(List<Trait> traits, TraitValidatorErrorInterface traitValidatorErrors) {

        ValidationErrors errors = new ValidationErrors();

        for (int i = 0; i < traits.size(); i++) {

            Trait trait = traits.get(i);
            Method method = trait.getMethod();
            Scale scale = trait.getScale();

            if (method == null) {
                ValidationError error = traitValidatorErrors.getMissingMethodMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            } else {
                if (isBlank(method.getMethodClass()) || method.getMethodClass() == null) {
                    ValidationError error = traitValidatorErrors.getMissingMethodClassMsg();
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                }
            }

            if (scale == null) {
                ValidationError error = traitValidatorErrors.getMissingScaleMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            } else {
                if (scale.getDataType() != null && scale.getDataType() == DataType.NUMERICAL &&
                        (isBlank(scale.getUnits()))) {
                    ValidationError error = traitValidatorErrors.getMissingScaleUnitMsg();
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                }
                if (scale.getDataType() == null) {
                    ValidationError error = traitValidatorErrors.getMissingScaleDataTypeMsg();
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                }
            }

            if (isBlank(trait.getObservationVariableName()) || trait.getObservationVariableName() == null) {
                ValidationError error = traitValidatorErrors.getMissingObsVarNameMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }
            if (isBlank(trait.getEntity()) || trait.getEntity() == null) {
                ValidationError error = traitValidatorErrors.getMissingTraitEntityMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }
            if (isBlank(trait.getAttribute()) || trait.getAttribute() == null) {
                ValidationError error = traitValidatorErrors.getMissingTraitAttributeMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }
            if (isBlank(trait.getTraitDescription()) || trait.getTraitDescription() == null) {
                ValidationError error = traitValidatorErrors.getMissingTraitDescriptionMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }
        }

        return errors;

    }

    public ValidationErrors checkTraitDataConsistency(List<Trait> traits, TraitValidatorErrorInterface traitValidatorErrors) {

        ValidationErrors errors = new ValidationErrors();

        for (int i = 0; i < traits.size(); i++){

            Trait trait = traits.get(i);
            Method method = trait.getMethod();
            Scale scale = trait.getScale();

            if (method != null && method.getMethodClass() != null && method.getMethodClass().equalsIgnoreCase(Method.COMPUTATION_TYPE)) {
                if (isBlank(method.getFormula()) || method.getFormula() == null) {
                    ValidationError error = traitValidatorErrors.getMissingMethodFormulaMsg();
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                }
            }

            if (scale != null && scale.getDataType() != null && (scale.getDataType() == DataType.ORDINAL || scale.getDataType() == DataType.NOMINAL)) {
                if (scale.getCategories() == null || scale.getCategories().isEmpty()) {
                    ValidationError error = traitValidatorErrors.getMissingScaleCategoriesMsg(scale.getDataType());
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                } else {

                    ValidationErrors categoryErrors = new ValidationErrors();

                    // Check the categories to make sure they are formatted properly
                    for (int k = 0; k < scale.getCategories().size(); k++) {

                        if (scale.getDataType() == DataType.ORDINAL) {
                            if (isBlank(scale.getCategories().get(k).getLabel())) {
                                ValidationError error = traitValidatorErrors.getBlankScaleCategoryLabelMsg();
                                categoryErrors.addError(k, error);
                            }
                        }

                        if (scale.getDataType() == DataType.NOMINAL) {
                            if (!isBlank(scale.getCategories().get(k).getLabel())) {
                                ValidationError error = traitValidatorErrors.getPopulatedNominalCategoryLabelMsg();
                                categoryErrors.addError(k, error);
                            }
                        }

                        if (isBlank(scale.getCategories().get(k).getValue())) {
                            ValidationError error = traitValidatorErrors.getBlankScaleCategoryValueMsg();
                            categoryErrors.addError(k, error);
                        }
                    }

                    if (categoryErrors.hasErrors()) {
                        ValidationError categoryError = traitValidatorErrors.getBadScaleCategory();
                        categoryError.setRowErrors(categoryErrors.getRowErrors());
                        errors.addError(traitValidatorErrors.getRowNumber(i), categoryError);
                    }

                    //Check if sufficient categories if scale is ordinal (2) or nominal (1)
                    if ((scale.getDataType() == DataType.NOMINAL) && (scale.getCategories().size() < 1)) {
                        ValidationError InsufficientNominalValError = traitValidatorErrors.getInsufficientNominalValError();
                        errors.addError(traitValidatorErrors.getRowNumber(i), InsufficientNominalValError);
                    } else if ((scale.getDataType() == DataType.ORDINAL) && (scale.getCategories().size() < 2)) {
                        ValidationError InsufficientOrdinalValError = traitValidatorErrors.getInsufficientOrdinalValError();
                        errors.addError(traitValidatorErrors.getRowNumber(i), InsufficientOrdinalValError);
                    }
                }
            }

            if (scale != null) {
                if (scale.getValidValueMax() != null && scale.getValidValueMin() != null) {
                    // Check if max < min
                    if (scale.getValidValueMax().compareTo(scale.getValidValueMin()) <= 0) {
                        ValidationError minMaxError = traitValidatorErrors.getMaxLessThenMinError();
                        errors.addError(traitValidatorErrors.getRowNumber(i), minMaxError);
                    }
                }
            }
        }

        return errors;
    }

    public ValidationErrors checkTraitFieldsLength(List<Trait> traits, TraitValidatorErrorInterface traitValidatorErrors) {

        ValidationErrors errors = new ValidationErrors();

        for (int i = 0; i < traits.size(); i++) {

            Trait trait = traits.get(i);
            Method method = trait.getMethod();
            int shortCharLimit = 16;
            int longCharLimit = 30;

            if ((trait.getObservationVariableName() != null) && (trait.getObservationVariableName().length() > shortCharLimit)) {
                ValidationError error = traitValidatorErrors.getCharLimitObsVarNameMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }
            if ((trait.getEntity() != null) && (trait.getEntity().length() > longCharLimit)) {
                ValidationError error = traitValidatorErrors.getCharLimitTraitEntityMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }
            if ((trait.getAttribute() != null) && (trait.getAttribute().length() > longCharLimit)) {
                ValidationError error = traitValidatorErrors.getCharLimitTraitAttributeMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }
            if ((method.getDescription() != null) && (method.getDescription().length() > longCharLimit)) {
                ValidationError error = traitValidatorErrors.getCharLimitMethodDescriptionMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }

        }
        return errors;
    }
    public ValidationErrors checkTraitFieldsFormat(List<Trait> traits, TraitValidatorErrorInterface traitValidatorErrors) {

        ValidationErrors errors = new ValidationErrors();

        for (int i = 0; i < traits.size(); i++) {

            Trait trait = traits.get(i);
            String name = trait.getObservationVariableName();

            Pattern pattern = Pattern.compile("[\\.\\]\\[]");
            Matcher matcher = pattern.matcher(name);
            boolean containsInvalidCharacter = matcher.find();

            if (name != null && containsInvalidCharacter){
                ValidationError error = traitValidatorErrors.getInvalidCharObsVarNameMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }
        }
        return errors;
    }

    public List<Trait> checkDuplicateTraitsExistingByName(UUID programId, List<Trait> traits){

        List<Trait> duplicates = new ArrayList<>();

        // Check for existing trait name
        List<Trait> duplicateNameTraits = checkForDuplicateTraitsByNames(programId, traits);

        for (int i = 0; i < traits.size(); i++) {
            Trait trait = traits.get(i);
            Boolean isDuplicate = duplicateNameTraits.stream().filter(duplicateTrait ->
                    duplicateTrait.getObservationVariableName().toLowerCase().strip().equals(trait.getObservationVariableName().toLowerCase().strip())
                    && !duplicateTrait.getId().equals(trait.getId())
            ).collect(Collectors.toList()).size() > 0;

            if (isDuplicate) {
                duplicates.add(trait);
            }
        }

        return duplicates;
    }

    public ValidationErrors checkDuplicateTraitsInFile(List<Trait> traits, TraitValidatorErrorInterface traitValidatorErrors){

        ValidationErrors errors = new ValidationErrors();
        // Check duplicate trait names
        Map<String, List<Integer>> namesMap = new HashMap<>();
        for (Integer i = 0; i < traits.size(); i++) {
            Trait trait = traits.get(i);
            if (trait.getObservationVariableName() != null){
                String key = trait.getObservationVariableName().toLowerCase();
                if (namesMap.containsKey(key)) {
                    namesMap.get(key).add(i);
                } else {
                    List<Integer> indexArray = new ArrayList<>();
                    indexArray.add(i);
                    namesMap.put(key, indexArray);
                }
            }
        }

        // Generate duplicate name errors
        for (String name: namesMap.keySet()){
            if (namesMap.get(name).size() > 1){
                for (Integer rowIndex: namesMap.get(name)){
                    ValidationError validationError = traitValidatorErrors.getDuplicateTraitsByNameInFileMsg(namesMap.get(name));
                    errors.addError(traitValidatorErrors.getRowNumber(rowIndex), validationError);
                }
            }
        }

        return errors;
    }

    private List<Trait> checkForDuplicateTraitsByNames(UUID programId, List<Trait> traits) {
        return traitDAO.getTraitsByTraitName(programId, traits);
    }

    public Optional<ValidationErrors> checkAllTraitValidations(List<Trait> traits, TraitValidatorErrorInterface traitValidatorError) {

        ValidationErrors validationErrors = new ValidationErrors();
        // Validate the traits
        ValidationErrors requiredFieldErrors = checkRequiredTraitFields(traits, traitValidatorError);
        ValidationErrors dataConsistencyErrors = checkTraitDataConsistency(traits, traitValidatorError);
        ValidationErrors duplicateTraitsInFile = checkDuplicateTraitsInFile(traits, traitValidatorError);
        ValidationErrors fieldLengthError =  checkTraitFieldsLength(traits, traitValidatorError);
        ValidationErrors fieldFormatErrors =  checkTraitFieldsFormat(traits, traitValidatorError);
        validationErrors.mergeAll(requiredFieldErrors, dataConsistencyErrors, duplicateTraitsInFile, fieldLengthError, fieldFormatErrors);

        if (validationErrors.hasErrors()){
            return Optional.of(validationErrors);
        } else {
            return Optional.empty();
        }
    }
}
