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

package org.breedinginsight.services;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.tables.pojos.MethodEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramObservationLevelEntity;
import org.breedinginsight.dao.db.tables.pojos.ScaleEntity;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.daos.*;
import org.breedinginsight.model.*;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.validators.TraitValidatorError;
import org.breedinginsight.services.validators.TraitValidatorErrorInterface;
import org.breedinginsight.services.validators.TraitValidatorService;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class TraitService {

    private TraitDAO traitDAO;
    private MethodDAO methodDAO;
    private ScaleDAO scaleDAO;
    private ProgramService programService;
    private ProgramOntologyService programOntologyService;
    private ProgramObservationLevelService programObservationLevelService;
    private UserService userService;
    private TraitValidatorService traitValidator;
    private DSLContext dsl;
    private TraitValidatorError traitValidatorError;

    @Inject
    public TraitService(TraitDAO traitDao, MethodDAO methodDao, ScaleDAO scaleDao, ProgramService programService,
                        ProgramOntologyService programOntologyService, ProgramObservationLevelService programObservationLevelService,
                        UserService userService, TraitValidatorService traitValidator, DSLContext dsl, TraitValidatorError traitValidatorError) {
        this.traitDAO = traitDao;
        this.methodDAO = methodDao;
        this.scaleDAO = scaleDao;
        this.programService = programService;
        this.programOntologyService = programOntologyService;
        this.programObservationLevelService = programObservationLevelService;
        this.userService = userService;
        this.traitValidator = traitValidator;
        this.dsl = dsl;
        this.traitValidatorError = traitValidatorError;
    }

    public List<Trait> getByProgramId(UUID programId, Boolean getFullTrait) throws DoesNotExistException {

        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program does not exist");
        }

        if (getFullTrait){
            return traitDAO.getTraitsFullByProgramId(programId);
        } else {
            return traitDAO.getTraitsByProgramId(programId);
        }

    }

    public Optional<Trait> getById(UUID programId, UUID traitId) throws DoesNotExistException {

        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program does not exist");
        }

       return traitDAO.getTraitFull(programId, traitId);
    }

    public List<Trait> createTraits(UUID programId, List<Trait> traits, AuthenticatedUser actingUser)
            throws DoesNotExistException, ValidatorException {

        Optional<Program> optionalProgram = programService.getById(programId);
        if (!optionalProgram.isPresent()) {
            throw new DoesNotExistException("Program does not exist");
        }
        Program program = optionalProgram.get();

        Optional<User> optionalUser = userService.getById(actingUser.getId());
        if (!optionalUser.isPresent()){
            throw new InternalServerException("Could not find user in system");
        }
        User user = optionalUser.get();


        // Get our ontology
        Optional<ProgramOntology> programOntologyOptional = programOntologyService.getByProgramId(programId);
        if (!programOntologyOptional.isPresent()){
            throw new InternalServerException("Ontology does not exist for program");
        }
        ProgramOntology programOntology = programOntologyOptional.get();

        ValidationErrors validationErrors = new ValidationErrors();
        try {
            assignTraitsProgramObservationLevel(traits, programId, traitValidatorError);
        } catch (ValidatorException e){
            validationErrors.merge(e.getErrors());
        }

        // Ignore duplicate traits
        ValidationErrors duplicateErrors = new ValidationErrors();
        List<Trait> duplicateTraits = traitValidator.checkDuplicateTraitsExistingByName(traits);
        List<Trait> duplicateTraitsByAbbrev = traitValidator.checkDuplicateTraitsExistingByAbbreviation(traits);
        List<Integer> traitIndexToRemove = new ArrayList<>();
        for (Trait duplicateTrait: duplicateTraits){

            Integer i = traits.indexOf(duplicateTrait);
            if (i == -1){
                throw new InternalServerException("Duplicate trait was not referenced correctly");
            } else {
                duplicateErrors.addError(i, traitValidatorError.getDuplicateTraitByNamesMsg());
                traitIndexToRemove.add(i);
            }
        }

        for (Trait duplicateTraitAbbrev: duplicateTraitsByAbbrev){

            Integer i = traits.indexOf(duplicateTraitAbbrev);
            if (i == -1){
                throw new InternalServerException("Duplicate trait was not referenced correctly");
            } else {
                duplicateErrors.addError(i, traitValidatorError.getDuplicateTraitByAbbreviationsMsg());
                traitIndexToRemove.add(i);
            }
        }

        // Check the rest of our validations
        Optional<ValidationErrors> optionalValidationErrors = traitValidator.checkAllTraitValidations(traits, traitValidatorError);
        if (optionalValidationErrors.isPresent()){
            validationErrors.merge(optionalValidationErrors.get());
        }

        // Remove our duplicate traits
        Set<Trait> duplicateTraitSet = new HashSet<>(duplicateTraits);
        duplicateTraitSet.addAll(duplicateTraitsByAbbrev);
        traits.removeAll(duplicateTraitSet);

        if (validationErrors.hasErrors()){
            // If there are other errors, show our duplicate errors
            validationErrors.merge(duplicateErrors);
            throw new ValidatorException(validationErrors);
        }

        // Create the traits
        List<Trait> createdTraits = new ArrayList<>();

        if (traits.size() > 0) {

            createdTraits = dsl.transactionResult(configuration -> {

                //TODO: If one trait in brapi fails, roll back all others before it
                for (Trait trait : traits) {
                    // Create method
                    MethodEntity jooqMethod = MethodEntity.builder()
                            .methodName(trait.getMethod().getMethodName())
                            .programOntologyId(programOntology.getId())
                            .createdBy(actingUser.getId())
                            .updatedBy(actingUser.getId())
                            .build();
                    methodDAO.insert(jooqMethod);
                    trait.getMethod().setId(jooqMethod.getId());

                    // Create scale
                    ScaleEntity jooqScale = ScaleEntity.builder()
                            .scaleName(trait.getScale().getScaleName())
                            .dataType(trait.getScale().getDataType())
                            .programOntologyId(programOntology.getId())
                            .createdBy(actingUser.getId())
                            .updatedBy(actingUser.getId())
                            .build();
                    scaleDAO.insert(jooqScale);
                    trait.getScale().setId(jooqScale.getId());

                    // Create trait
                    TraitEntity jooqTrait = TraitEntity.builder()
                            .traitName(trait.getTraitName())
                            .abbreviations(trait.getAbbreviations())
                            .programOntologyId(programOntology.getId())
                            .programObservationLevelId(trait.getProgramObservationLevel().getId())
                            .methodId(jooqMethod.getId())
                            .scaleId(jooqScale.getId())
                            .createdBy(actingUser.getId())
                            .updatedBy(actingUser.getId())
                            .build();
                    traitDAO.insert(jooqTrait);
                    trait.setId(jooqTrait.getId());
                }

                return traitDAO.createTraitsBrAPI(traits, user, program);
            });
        }

        return createdTraits;
    }

    public void assignTraitsProgramObservationLevel(List<Trait> traits, UUID programId, TraitValidatorErrorInterface traitValidatorError) throws ValidatorException {

        ValidationErrors validationErrors = new ValidationErrors();

        // Get our program observation levels
        List<ProgramObservationLevel> programLevels = programObservationLevelService.getByProgramId(programId);
        List<String> availableLevels = programLevels.stream().map(ProgramObservationLevelEntity::getName).collect(Collectors.toList());
        for (int i = 0; i < traits.size(); i++) {
            Trait trait = traits.get(i);
            if (trait.getProgramObservationLevel() != null){
                List<ProgramObservationLevel> matchingLevels = programLevels.stream()
                        .filter(programObservationLevel -> programObservationLevel.getName().equals(trait.getProgramObservationLevel().getName()))
                        .collect(Collectors.toList());
                if (matchingLevels.size() == 0) {
                    ValidationError validationError = traitValidatorError.getTraitLevelDoesNotExist(availableLevels);
                    validationErrors.addError(i, validationError);
                } else {
                    ProgramObservationLevel dbLevel = matchingLevels.get(0);
                    trait.getProgramObservationLevel().setId(dbLevel.getId());
                }
            }
        }

        if (validationErrors.hasErrors()){
            throw new ValidatorException(validationErrors);
        }

    }
}
