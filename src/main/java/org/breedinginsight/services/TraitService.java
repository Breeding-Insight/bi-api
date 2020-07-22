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
import org.breedinginsight.dao.db.tables.pojos.ScaleEntity;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;
import org.breedinginsight.daos.*;
import org.breedinginsight.model.*;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.services.validators.TraitValidator;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class TraitService {

    @Inject
    private TraitDAO traitDAO;
    @Inject
    private MethodDAO methodDAO;
    @Inject
    private ScaleDAO scaleDAO;
    @Inject
    private ProgramService programService;
    @Inject
    private ProgramOntologyService programOntologyService;
    @Inject
    private ProgramObservationLevelService programObservationLevelService;
    @Inject
    private UserService userService;
    @Inject
    private TraitValidator traitValidator;
    @Inject
    private DSLContext dsl;

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

        // Get our program observation levels
        List<ProgramObservationLevel> programLevels = programObservationLevelService.getByProgramId(programId);
        for (int i = 0; i < traits.size(); i++) {
            Trait trait = traits.get(i);
            if (trait.getProgramObservationLevel() != null){
                List<ProgramObservationLevel> matchingLevels = programLevels.stream()
                        .filter(programObservationLevel -> programObservationLevel.getName().equals(trait.getProgramObservationLevel().getName()))
                        .collect(Collectors.toList());
                if (matchingLevels.size() == 0) {
                    ValidationError validationError = new ValidationError("programObservationLevel.name",
                            "Program Observation Level does not exist.", HttpStatus.NOT_FOUND);
                    validationErrors.addError(i, validationError);
                } else {
                    ProgramObservationLevel dbLevel = matchingLevels.get(0);
                    trait.getProgramObservationLevel().setId(dbLevel.getId());
                }
            }
        }

        // Validate the traits
        ValidationErrors requiredFieldErrors = TraitValidator.checkRequiredTraitFields(traits);
        ValidationErrors dataConsistencyErrors = TraitValidator.checkTraitDataConsistency(traits);
        ValidationErrors duplicateTraits = traitValidator.checkDuplicateTraitsExisting(traits);
        ValidationErrors duplicateTraitsInFile = TraitValidator.checkDuplicateTraitsInFile(traits);
        validationErrors.mergeAll(requiredFieldErrors, dataConsistencyErrors, duplicateTraits, duplicateTraitsInFile);

        if (validationErrors.hasErrors()){
            throw new ValidatorException(validationErrors);
        }

        // Create the traits
        List<Trait> createdTraits = dsl.transactionResult(configuration -> {

            //TODO: If one trait in brapi fails, roll back all others before it
            for (Trait trait: traits) {
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


        return createdTraits;
    }
}
