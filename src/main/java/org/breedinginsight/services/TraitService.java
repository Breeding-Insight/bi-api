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

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.daos.ProgramOntologyDAO;
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.model.ProgramOntology;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.validators.TraitValidator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class TraitService {

    @Inject
    private TraitDAO traitDAO;
    @Inject
    private ProgramService programService;
    @Inject
    private ProgramOntologyService programOntologyService;

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

    public List<Trait> createTraits(UUID programId, List<Trait> traits) throws DoesNotExistException, UnprocessableEntityException {

        if (!programService.exists(programId)) {
            throw new DoesNotExistException("Program does not exist");
        }

        // Get our ontology
        Optional<ProgramOntology> programOntologyOptional = programOntologyService.getByProgramId(programId);
        if (!programOntologyOptional.isPresent()){
            throw new InternalServerException("Ontology does note exist for program");
        }
        ProgramOntology programOntology = programOntologyOptional.get();

        // Check the traits are legit
        //TODO: See if we need to specify data row that throws an error
        for (Trait trait: traits) {
            TraitValidator.checkRequiredTraitFields(trait);
            TraitValidator.checkTraitDataConsistency(trait);
        }

        // Other checks to think about... Duplicates
        //TODO: Check for existing trait name
        //TODO: Check for duplicate trait abbreviations? Would have to be through brapi

        // Create the traits
        return traitDAO.createTraits(programOntology.getId(), traits);
    }
}
