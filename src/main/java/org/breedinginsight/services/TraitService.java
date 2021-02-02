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

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.tables.pojos.MethodEntity;
import org.breedinginsight.dao.db.tables.pojos.ScaleEntity;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.daos.*;
import org.breedinginsight.model.*;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.validators.TraitValidatorError;
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

    public List<Trait> getByProgramId(UUID programId, boolean getFullTrait) throws DoesNotExistException {

        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program does not exist");
        }

        if (getFullTrait){
            return traitDAO.getTraitsFullByProgramId(programId);
        } else {
            return traitDAO.getTraitsByProgramId(programId);
        }

    }

    public List<Trait> getByProgramIds(List<UUID> programIds, boolean getFullTrait) throws DoesNotExistException {

        if (programIds.stream().anyMatch(programId -> programService.exists(programId) == false)) {
            throw new DoesNotExistException("Program does not exist");
        }

        if (getFullTrait){
            return traitDAO.getTraitsFullByProgramIds(programIds);
        } else {
            return traitDAO.getTraitsByProgramIds(programIds.toArray(UUID[]::new));
        }

    }

    public Optional<Trait> getById(UUID programId, UUID traitId) throws DoesNotExistException {

        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program does not exist");
        }

       return traitDAO.getTraitFull(programId, traitId);
    }

    public List<Trait> createTraits(UUID programId, List<Trait> traits, AuthenticatedUser actingUser, Boolean throwDuplicateErrors)
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

        // Clear trait ids for proper validation against db
        traits.stream().forEach(trait -> trait.setId(null));
        // Validate and remove duplicates if specified
        checkAndPrepareTraits(programId, traits, throwDuplicateErrors);

        // Create the traits
        List<Trait> createdTraits = new ArrayList<>();

        if (traits.size() > 0) {

            createdTraits = dsl.transactionResult(configuration -> {

                // Create the new observation levels
                Map<String, ProgramObservationLevel> createdLevelsMap = checkAndCreateObservationLevels(programId, traits, actingUser);

                //TODO: If one trait in brapi fails, roll back all others before it
                for (Trait trait : traits) {

                    // Create method
                    MethodEntity jooqMethod = MethodEntity.builder()
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

                    // Check observation level
                    if (trait.getProgramObservationLevel().getId() == null) {
                        ProgramObservationLevel level = createdLevelsMap.get(trait.getProgramObservationLevel().getName());
                        trait.setProgramObservationLevel(level);
                    }

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

    public void assignTraitsProgramObservationLevel(List<Trait> traits, UUID programId) throws DoesNotExistException {

        // Get our program observation levels
        List<ProgramObservationLevel> programLevels = programObservationLevelService.getByProgramId(programId);
        for (int i = 0; i < traits.size(); i++) {
            Trait trait = traits.get(i);
            if (trait.getProgramObservationLevel() != null){
                List<ProgramObservationLevel> matchingLevels = programLevels.stream()
                        .filter(programObservationLevel -> programObservationLevel.getName().equalsIgnoreCase(trait.getProgramObservationLevel().getName()))
                        .collect(Collectors.toList());
                if (matchingLevels.size() == 0) {
                    // If doesn't exist, save it without an id. We will create it later
                    ProgramObservationLevel programObservationLevel = new ProgramObservationLevel();
                    programObservationLevel.setName(StringUtils.capitalize(trait.getProgramObservationLevel().getName().toLowerCase()));
                    trait.setProgramObservationLevel(programObservationLevel);
                } else {
                    ProgramObservationLevel dbLevel = matchingLevels.get(0);
                    trait.getProgramObservationLevel().setId(dbLevel.getId());
                }
            }
        }
    }

    public void preprocessTraits(List<Trait> traits) {

        // Set data type to numerical when method class is computation
        for (Trait trait: traits) {
            if (trait.getMethod() != null && trait.getMethod().getMethodClass() != null &&
                trait.getMethod().getMethodClass().equalsIgnoreCase(Method.COMPUTATION_TYPE)) {
                if (trait.getScale() != null) {
                    trait.getScale().setDataType(DataType.NUMERICAL);
                }
            }
        }

    }

    private void checkAndPrepareTraits(UUID programId, List<Trait> traits, Boolean throwDuplicateErrors)
            throws ValidatorException {

        // Preprocessing
        preprocessTraits(traits);

        ValidationErrors validationErrors = new ValidationErrors();

        // Ignore duplicate traits
        ValidationErrors duplicateErrors = new ValidationErrors();
        List<Trait> duplicateTraits = traitValidator.checkDuplicateTraitsExistingByName(programId, traits);
        List<Trait> duplicateTraitsByAbbrev = traitValidator.checkDuplicateTraitsExistingByAbbreviation(programId, traits);
        List<Integer> traitIndexToRemove = new ArrayList<>();
        for (Trait duplicateTrait: duplicateTraits){

            Integer i = traits.indexOf(duplicateTrait);
            if (i == -1){
                throw new InternalServerException("Duplicate trait was not referenced correctly");
            } else {
                duplicateErrors.addError(traitValidatorError.getRowNumber(i), traitValidatorError.getDuplicateTraitByNamesMsg());
                traitIndexToRemove.add(i);
            }
        }

        for (Trait duplicateTraitAbbrev: duplicateTraitsByAbbrev){

            Integer i = traits.indexOf(duplicateTraitAbbrev);
            if (i == -1){
                throw new InternalServerException("Duplicate trait was not referenced correctly");
            } else {
                duplicateErrors.addError(traitValidatorError.getRowNumber(i), traitValidatorError.getDuplicateTraitByAbbreviationsMsg());
                traitIndexToRemove.add(i);
            }
        }

        // Check the rest of our validations
        Optional<ValidationErrors> optionalValidationErrors = traitValidator.checkAllTraitValidations(traits, traitValidatorError);
        if (optionalValidationErrors.isPresent()){
            validationErrors.merge(optionalValidationErrors.get());
        }

        // Remove our duplicate traits if we are not going to throw an error on them
        if (!throwDuplicateErrors) {
            Set<Trait> duplicateTraitSet = new HashSet<>(duplicateTraits);
            duplicateTraitSet.addAll(duplicateTraitsByAbbrev);
            traits.removeAll(duplicateTraitSet);
        }

        // Throw validation errors
        if (validationErrors.hasErrors() || (throwDuplicateErrors && duplicateErrors.hasErrors())){
            // If there are other errors, show our duplicate errors
            validationErrors.merge(duplicateErrors);
            throw new ValidatorException(validationErrors);
        }
    }

    private Map<String, ProgramObservationLevel> checkAndCreateObservationLevels(UUID programId, List<Trait> traits, AuthenticatedUser actingUser)
            throws DoesNotExistException {

        assignTraitsProgramObservationLevel(traits, programId);
        List<String> newObservationLevels = traits.stream()
                .filter(trait -> trait.getProgramObservationLevel().getId() == null)
                .map(trait -> trait.getProgramObservationLevel().getName())
                .distinct()
                .collect(Collectors.toList());
        Map<String, ProgramObservationLevel> createdLevelsMap = new HashMap<>();
        try {
            List<ProgramObservationLevel> createdLevels = programObservationLevelService.createLevels(programId, newObservationLevels, actingUser);
            createdLevels.stream().forEach(level -> createdLevelsMap.put(level.getName(), level));
        } catch (DoesNotExistException e) {
            throw new HttpServerException("Could not find program");
        }
        return createdLevelsMap;
    }

    public List<Trait> updateTraits(UUID programId, List<Trait> traits, AuthenticatedUser actingUser)
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

        // Retrieve all of our traits
        List<TraitEntity> existingTraitEntities = new ArrayList<>();
        ValidationErrors missingTraitValidationErrors = new ValidationErrors();
        for (int i = 0; i < traits.size(); i++) {
            TraitEntity existingTrait = traitDAO.fetchOneById(traits.get(i).getId());
            if (existingTrait != null) {
                existingTraitEntities.add(existingTrait);
            } else {
                missingTraitValidationErrors.addError(i, traitValidatorError.getTraitIdDoesNotExistMsg());
            }
        }

        try {
            checkAndPrepareTraits(programId, traits, true);
        } catch (ValidatorException e) {
            e.getErrors().merge(missingTraitValidationErrors);
            throw e;
        }


        // Create the traits
        List<Trait> updatedTraits = new ArrayList<>();

        if (traits.size() > 0) {

            updatedTraits = dsl.transactionResult(configuration -> {

                // Create the new observation levels
                Map<String, ProgramObservationLevel> createdLevelsMap = checkAndCreateObservationLevels(programId, traits, actingUser);
                List<Trait> updatedResults = new ArrayList<>();
                for (int i = 0; i < traits.size(); i++) {

                    Trait updatedTrait = traits.get(i);
                    TraitEntity existingTraitEntity = existingTraitEntities.get(i);

                    // Check observation level
                    if (updatedTrait.getProgramObservationLevel().getId() == null) {
                        ProgramObservationLevel level = createdLevelsMap.get(updatedTrait.getProgramObservationLevel().getName());
                        updatedTrait.setProgramObservationLevel(level);
                    }

                    // We don't store any info on methods.
                    // Jump to scale
                    ScaleEntity existingScaleEntity = scaleDAO.fetchOneById(existingTraitEntity.getScaleId());
                    existingScaleEntity.setScaleName(updatedTrait.getScale().getScaleName());
                    existingScaleEntity.setDataType(updatedTrait.getScale().getDataType());
                    existingScaleEntity.setUpdatedBy(user.getId());
                    scaleDAO.update(existingScaleEntity);

                    // Update trait
                    existingTraitEntity.setTraitName(updatedTrait.getTraitName());
                    existingTraitEntity.setAbbreviations(updatedTrait.getAbbreviations());
                    existingTraitEntity.setProgramObservationLevelId(updatedTrait.getProgramObservationLevel().getId());
                    existingTraitEntity.setUpdatedBy(user.getId());
                    traitDAO.update(existingTraitEntity);

                    // Update in brapi
                    Trait updatedTraitResult = traitDAO.updateTraitBrAPI(updatedTrait, program);
                    updatedResults.add(updatedTraitResult);
                }
                return updatedResults;
            });
        }

        return updatedTraits;
    }
}
