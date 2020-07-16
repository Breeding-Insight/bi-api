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

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.dao.db.tables.pojos.*;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.ProgramObservationLevelDAO;
import org.breedinginsight.daos.ProgramOntologyDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class ProgramService {

    @Inject
    private ProgramDAO dao;
    @Inject
    private ProgramOntologyDAO programOntologyDAO;
    @Inject
    private ProgramObservationLevelDAO programObservationLevelDAO;
    @Inject
    private SpeciesService speciesService;
    @Inject
    private DSLContext dsl;

    public Optional<Program> getById(UUID programId) {

        List<Program> programs = dao.get(programId);

        if (programs.size() <= 0) {
            return Optional.empty();
        }

        return Optional.of(programs.get(0));
    }

    public List<Program> getAll(){
        /* Get all of the programs */

        List<ProgramEntity> programEntities = dao.fetchByActive(true);
        List<Program> programs = dao.getFromEntity(programEntities);

        return programs;
    }

    public Program create(ProgramRequest programRequest, AuthenticatedUser actingUser) throws UnprocessableEntityException {
        /* Create a program from a request object */

        // Check that our species exists
        SpeciesRequest speciesRequest = programRequest.getSpecies();
        if (!speciesService.exists(speciesRequest.getId())){
            throw new UnprocessableEntityException("Species does not exist");
        }

        Program program = dsl.transactionResult(configuration -> {
            // Parse and create the program object
            ProgramEntity programEntity = ProgramEntity.builder()
                    .name(programRequest.getName())
                    .speciesId(speciesRequest.getId())
                    .abbreviation(programRequest.getAbbreviation())
                    .objective(programRequest.getObjective())
                    .documentationUrl(programRequest.getDocumentationUrl())
                    .createdBy(actingUser.getId())
                    .updatedBy(actingUser.getId())
                    .build();

            // Insert and update
            dao.insert(programEntity);

            ProgramOntologyEntity programOntologyEntity = ProgramOntology.builder()
                    .programId(programEntity.getId())
                    .createdBy(actingUser.getId())
                    .updatedBy(actingUser.getId())
                    .build();
            programOntologyDAO.insert(programOntologyEntity);

            //TODO: Remove this insert when we have an endpoint for it
            ProgramObservationLevelEntity programObservationLevelEntity = ProgramObservationLevelEntity.builder()
                    .name("Plant")
                    .programId(programEntity.getId())
                    .createdBy(actingUser.getId())
                    .updatedBy(actingUser.getId())
                    .build();
            programObservationLevelDAO.insert(programObservationLevelEntity);

            //TODO: Add program and ontology to brapi services.
            //TODO: Add species to brapi service if it doesn't exist

            return dao.get(programEntity.getId()).get(0);
        });


        return program;
    }

    public Program update(UUID programId, ProgramRequest programRequest, AuthenticatedUser actingUser) throws DoesNotExistException, UnprocessableEntityException {
        /* Update an existing program */

        ProgramEntity programEntity = dao.fetchOneById(programId);
        if (programEntity == null){
            throw new DoesNotExistException("Program does not exist");
        }

        // Check that our species exists
        SpeciesRequest speciesRequest = programRequest.getSpecies();
        if (!speciesService.exists(speciesRequest.getId())){
            throw new UnprocessableEntityException("Species does not exist");
        }

        // Parse and create the program object
        programEntity.setName(programRequest.getName());
        programEntity.setSpeciesId(speciesRequest.getId());
        programEntity.setAbbreviation(programRequest.getAbbreviation());
        programEntity.setObjective(programRequest.getObjective());
        programEntity.setDocumentationUrl(programRequest.getDocumentationUrl());
        programEntity.setUpdatedAt(OffsetDateTime.now());
        programEntity.setUpdatedBy(actingUser.getId());

        dao.update(programEntity);
        Program program = dao.get(programEntity.getId()).get(0);

        return program;
    }

    public void archive(UUID programId, AuthenticatedUser actingUser) throws DoesNotExistException {
        /* Archive an existing program */

        ProgramEntity programEntity = dao.fetchOneById(programId);
        if (programEntity == null){
            throw new DoesNotExistException("Program does not exist");
        }

        programEntity.setActive(false);
        programEntity.setUpdatedBy(actingUser.getId());
        programEntity.setUpdatedAt(OffsetDateTime.now());
        dao.update(programEntity);
    }

    public boolean exists(UUID programId) {
        return dao.existsById(programId);
    }

    public void delete(UUID programId) throws DoesNotExistException {
        /* Deletes an existing program */

        ProgramEntity programEntity = dao.fetchOneById(programId);
        if (programEntity == null){
            throw new DoesNotExistException("Program does not exist");
        }

        dao.delete(programEntity);
    }

    public ProgramBrAPIEndpoints getBrapiEndpoints(UUID programId) {
        return dao.getProgramBrAPIEndpoints();
    }

}
