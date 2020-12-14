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

import org.brapi.v2.phenotyping.model.BrApiScaleCategories;
import org.breedinginsight.api.model.v1.response.RowValidationErrors;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import ucar.nc2.grib.TimeCoordUnion;

import javax.inject.Inject;
import java.util.*;
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
                if (isBlank(method.getDescription()) || method.getDescription() == null) {
                    ValidationError error = traitValidatorErrors.getMissingMethodDescriptionMsg();
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                }
                if (isBlank(method.getMethodClass()) || method.getMethodClass() == null) {
                    ValidationError error = traitValidatorErrors.getMissingMethodClassMsg();
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                }
            }

            if (scale == null) {
                ValidationError error = traitValidatorErrors.getMissingScaleMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            } else {
                if (isBlank(scale.getScaleName()) || scale.getScaleName() == null) {
                    ValidationError error = traitValidatorErrors.getMissingScaleNameMsg();
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                }
                if (scale.getDataType() == null || scale.getDataType() == null) {
                    ValidationError error = traitValidatorErrors.getMissingScaleDataTypeMsg();
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                }
            }

            if (isBlank(trait.getTraitName()) || trait.getTraitName() == null) {
                ValidationError error = traitValidatorErrors.getMissingTraitNameMsg();
                errors.addError(traitValidatorErrors.getRowNumber(i), error);
            }
            if (trait.getProgramObservationLevel() == null || isBlank(trait.getProgramObservationLevel().getName())) {
                ValidationError error = traitValidatorErrors.getMissingProgramObservationLevelMsg();
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

            if (method != null && method.getMethodClass() != null && method.getMethodClass().equals(Method.COMPUTATION_TYPE)) {
                if (isBlank(method.getFormula()) || method.getFormula() == null) {
                    ValidationError error = traitValidatorErrors.getMissingMethodFormulaMsg();
                    errors.addError(traitValidatorErrors.getRowNumber(i), error);
                }
            }

            if (scale != null && scale.getDataType() != null && (scale.getDataType() == DataType.ORDINAL || scale.getDataType() == DataType.NOMINAL)) {
                if (scale.getCategories() == null || scale.getCategories().isEmpty()) {
                    ValidationError error = traitValidatorErrors.getMissingScaleCategoriesMsg();
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

                        if (isBlank(scale.getCategories().get(k).getValue())) {
                            ValidationError error = traitValidatorErrors.getBlankScaleCategoryValueMsg();
                            categoryErrors.addError(k, error);
                        }
                    }

                    if (categoryErrors.hasErrors()) {
                        ValidationError categoryError = traitValidatorErrors.getBadScaleCategory();
                        categoryError.setRowErrors(categoryErrors.getRowErrors());
                        errors.addError(i, categoryError);
                    }
                }
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
                    duplicateTrait.getTraitName().toLowerCase().strip().equals(trait.getTraitName().toLowerCase().strip())
            ).collect(Collectors.toList()).size() > 0;

            if (isDuplicate) {
                duplicates.add(trait);
            }
        }

        return duplicates;
    }

    public List<Trait> checkDuplicateTraitsExistingByAbbreviation(UUID programId, List<Trait> traits){

        List<Trait> duplicates = new ArrayList<>();

        // Check for existing trait abbreviations
        List<Trait> duplicateAbbreviationTraits = checkForDuplicatesTraitsByAbbreviation(programId, traits);

        for (int i = 0; i < traits.size(); i++){
            Trait trait = traits.get(i);

            Boolean isDuplicateAbbrev = false;
            if (trait.getAbbreviations() != null){
                for (String abbreviation: trait.getAbbreviations()){
                    isDuplicateAbbrev = duplicateAbbreviationTraits.stream().filter(duplicateAbbreviationTrait ->
                            List.of(duplicateAbbreviationTrait.getAbbreviations()).contains(abbreviation)
                    ).collect(Collectors.toList()).size() > 0;
                    break;
                }
            }

            if (isDuplicateAbbrev) {
                if (!duplicates.contains(trait)){
                    duplicates.add(trait);
                }
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
            if (trait.getTraitName() != null){
                String key = String.format("%s", trait.getTraitName().toLowerCase());
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

        // Check duplicate abbreviations
        Map<String, List<Integer>> abbreviationMap = new HashMap<>();
        for (Integer i = 0; i < traits.size(); i++){
            Trait trait = traits.get(i);
            if (trait.getAbbreviations() != null){
                for (String abbreviation: trait.getAbbreviations()){
                    if (abbreviationMap.containsKey(abbreviation)) {
                        abbreviationMap.get(abbreviation).add(i);
                    } else {
                        List<Integer> indexArray = new ArrayList<>();
                        indexArray.add(i);
                        abbreviationMap.put(abbreviation, indexArray);
                    }
                }
            }
        }

        // Generate duplicate abbreviation errors
        for (Integer i = 0; i < traits.size(); i++) {
            Trait trait = traits.get(i);
            if (trait.getAbbreviations() != null) {
                for (String abbreviation : trait.getAbbreviations()) {
                    if (abbreviationMap.containsKey(abbreviation)) {
                        if (abbreviationMap.get(abbreviation).size() > 1){
                            ValidationError validationError = traitValidatorErrors.getDuplicateTraitsByAbbreviationInFileMsg(abbreviationMap.get(abbreviation));
                            errors.addError(traitValidatorErrors.getRowNumber(i), validationError);
                            break;
                        }
                    }
                }
            }
        }

        return errors;
    }

    private List<Trait> checkForDuplicateTraitsByNames(UUID programId, List<Trait> traits) {
        return traitDAO.getTraitsByTraitName(programId, traits);
    }

    private List<Trait> checkForDuplicatesTraitsByAbbreviation(UUID programId, List<Trait> traits) {

        Set<String> abbreviationSet = new HashSet<>();
        for (Trait trait: traits) {
            if (trait.getAbbreviations() != null) {
                abbreviationSet.addAll(List.of(trait.getAbbreviations()));
            }
        }

        List<Trait> matchingTraits = new ArrayList<>();
        if (abbreviationSet.size() > 0){
            matchingTraits = traitDAO.getTraitsByAbbreviation(programId, List.of(abbreviationSet.toArray(String[]::new)));
        }

        return matchingTraits;
    }

    public Optional<ValidationErrors> checkAllTraitValidations(List<Trait> traits, TraitValidatorErrorInterface traitValidatorError) {

        ValidationErrors validationErrors = new ValidationErrors();
        // Validate the traits
        ValidationErrors requiredFieldErrors = checkRequiredTraitFields(traits, traitValidatorError);
        ValidationErrors dataConsistencyErrors = checkTraitDataConsistency(traits, traitValidatorError);
        ValidationErrors duplicateTraitsInFile = checkDuplicateTraitsInFile(traits, traitValidatorError);
        validationErrors.mergeAll(requiredFieldErrors, dataConsistencyErrors, duplicateTraitsInFile);

        if (validationErrors.hasErrors()){
            return Optional.of(validationErrors);
        } else {
            return Optional.empty();
        }
    }
}
