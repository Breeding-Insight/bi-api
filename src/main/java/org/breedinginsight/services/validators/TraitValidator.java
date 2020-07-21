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
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.ProgramObservationLevelService;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class TraitValidator {

    private TraitDAO traitDAO;

    @Inject
    public TraitValidator(TraitDAO traitDAO){
        this.traitDAO = traitDAO;
    }

    public static ValidationErrors checkRequiredTraitFields(List<Trait> traits) {

        ValidationErrors errors = new ValidationErrors();

        for (int i = 0; i < traits.size(); i++) {

            Trait trait = traits.get(i);
            Method method = trait.getMethod();
            Scale scale = trait.getScale();

            if (method == null) {
                ValidationError error = new ValidationError("method",
                        "Missing method", HttpStatus.UNPROCESSABLE_ENTITY);
                errors.addError(i, error);
            } else {
                if (isBlank(method.getMethodName()) || method.getMethodName() == null) {
                    ValidationError error = new ValidationError("method.methodName",
                            "Missing method name", HttpStatus.UNPROCESSABLE_ENTITY);
                    errors.addError(i, error);
                }
                if (isBlank(method.getDescription()) || method.getDescription() == null) {
                    ValidationError error = new ValidationError("method.description",
                            "Missing method description", HttpStatus.UNPROCESSABLE_ENTITY);
                    errors.addError(i, error);
                }
                if (isBlank(method.getMethodClass()) || method.getMethodClass() == null) {
                    ValidationError error = new ValidationError("method.methodClass",
                            "Missing method class", HttpStatus.UNPROCESSABLE_ENTITY);
                    errors.addError(i, error);
                }
            }

            if (scale == null) {
                ValidationError error = new ValidationError("scale",
                        "Missing scale", HttpStatus.UNPROCESSABLE_ENTITY);
                errors.addError(i, error);
            } else {
                if (isBlank(scale.getScaleName()) || scale.getScaleName() == null) {
                    ValidationError error = new ValidationError("scale.scaleName",
                            "Missing scale name", HttpStatus.UNPROCESSABLE_ENTITY);
                    errors.addError(i, error);
                }
                if (scale.getDataType() == null || scale.getDataType() == null) {
                    ValidationError error = new ValidationError("scale.dataType",
                            "Missing scale type", HttpStatus.UNPROCESSABLE_ENTITY);
                    errors.addError(i, error);
                }
            }

            if (isBlank(trait.getTraitName()) || trait.getTraitName() == null) {
                ValidationError error = new ValidationError("traitName",
                        "Missing trait name", HttpStatus.UNPROCESSABLE_ENTITY);
                errors.addError(i, error);
            }
            if (isBlank(trait.getDescription()) || trait.getDescription() == null) {
                ValidationError error = new ValidationError("description",
                        "Missing trait description", HttpStatus.UNPROCESSABLE_ENTITY);
                errors.addError(i, error);
            }
            if (trait.getProgramObservationLevel() == null || isBlank(trait.getProgramObservationLevel().getName())) {
                ValidationError error = new ValidationError("programObservationLevel.name",
                        "Missing trait level", HttpStatus.UNPROCESSABLE_ENTITY);
                errors.addError(i, error);
            }
        }

        return errors;

    }

    public static ValidationErrors checkTraitDataConsistency(List<Trait> traits) {

        ValidationErrors errors = new ValidationErrors();

        for (int i = 0; i < traits.size(); i++){

            Trait trait = traits.get(i);
            Method method = trait.getMethod();
            Scale scale = trait.getScale();

            if (method != null && method.getMethodClass() != null && method.getMethodClass().equals(Method.COMPUTATION_TYPE)) {
                if (isBlank(method.getFormula()) || method.getFormula() == null) {
                    ValidationError error = new ValidationError("method.formula",
                            "Missing formula for Computation method", HttpStatus.UNPROCESSABLE_ENTITY);
                    errors.addError(i, error);
                }
            }

            if (scale != null && scale.getDataType() != null && scale.getDataType() == DataType.ORDINAL) {
                if (scale.getCategories() == null || scale.getCategories().isEmpty()) {
                    ValidationError error = new ValidationError("scale.categories",
                            "Missing categories for Ordinal scale", HttpStatus.UNPROCESSABLE_ENTITY);
                    errors.addError(i, error);
                }
            }
        }

        return errors;
    }

    public ValidationErrors checkDuplicateTraits(List<Trait> traits) {

        ValidationErrors errors = new ValidationErrors();

        // Check for existing trait name
        List<Trait> duplicateNameTraits = checkForDuplicateTraitsByNames(traits);
        // Check for existing trait abbreviations
        List<Trait> duplicateAbbreviationTraits = checkForDuplicatesTraitsByAbbreviation(traits);

        for (int i = 0; i < traits.size(); i++){
            Trait trait = traits.get(i);
            Boolean isDuplicate = duplicateNameTraits.stream().filter(duplicateTrait ->
                    duplicateTrait.getTraitName().toLowerCase().equals(trait.getTraitName().toLowerCase()) &&
                            duplicateTrait.getScale().getScaleName().toLowerCase().equals(trait.getScale().getScaleName().toLowerCase()) &&
                            duplicateTrait.getMethod().getMethodName().toLowerCase().equals(trait.getMethod().getMethodName().toLowerCase())
            ).collect(Collectors.toList()).size() > 0;

            if (isDuplicate){
                //TODO: Figure out a better way to do field names
                ValidationError validationError = new ValidationError("traitName",
                        "Trait name - Scale name - Method name combination already exists",
                        HttpStatus.CONFLICT);
                errors.addError(i, validationError);
            }

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
                ValidationError validationError = new ValidationError("abbreviations",
                        "Trait abbreviation already exists",
                        HttpStatus.CONFLICT);
                errors.addError(i, validationError);
            }


        }

        return errors;
    }

    private List<Trait> checkForDuplicateTraitsByNames(List<Trait> traits) {
        return traitDAO.getTraitsByTraitMethodScaleName(traits);
    }

    private List<Trait> checkForDuplicatesTraitsByAbbreviation(List<Trait> traits) {
        Map<String, Trait> abbreviationMap = new HashMap<>();
        for (Trait trait: traits) {
            if (trait.getAbbreviations() != null){
                for (String abbreviation: trait.getAbbreviations()) {
                    abbreviationMap.put(abbreviation, trait);
                }
            }
        }

        List<Trait> matchingTraits = new ArrayList<>();
        if (abbreviationMap.size() > 0){
            matchingTraits = traitDAO.getTraitsByAbbreviation(List.of(abbreviationMap.keySet().toArray(String[]::new)));
        }

        return matchingTraits;
    }
}
