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
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.core.BrAPIProgram;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.dao.db.tables.pojos.*;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.ProgramObservationLevelDAO;
import org.breedinginsight.daos.ProgramOntologyDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.brapi.BrAPIClientProvider;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class ProgramService {

    private ProgramDAO dao;
    private ProgramOntologyDAO programOntologyDAO;
    private ProgramObservationLevelDAO programObservationLevelDAO;
    private SpeciesService speciesService;
    private DSLContext dsl;
    private SecurityService securityService;
    private BrAPIClientProvider brAPIClientProvider;

    private static final String PROGRAM_NAME_IN_USE = "PROGRAM_NAME_IN_USE";
    private static final String PROGRAM_KEY_IN_USE = "PROGRAM_KEY_IN_USE";
    private static final String GERMPLASM_SEQUENCE_TEMPLATE = "%s_germplasm_sequence";
    private static final String OBS_UNIT_SEQUENCE_TEMPLATE = "%s_obs_unit_sequence";
    private static final String EXP_SEQUENCE_TEMPLATE = "%s_exp_sequence";
    private static final String ENV_SEQUENCE_TEMPLATE = "%s_env_sequence";


    @Inject
    public ProgramService(ProgramDAO dao, ProgramOntologyDAO programOntologyDAO, ProgramObservationLevelDAO programObservationLevelDAO,
                          SpeciesService speciesService, DSLContext dsl, SecurityService securityService, BrAPIClientProvider brAPIClientProvider) {
        this.dao = dao;
        this.programOntologyDAO = programOntologyDAO;
        this.programObservationLevelDAO = programObservationLevelDAO;
        this.speciesService = speciesService;
        this.dsl = dsl;
        this.securityService = securityService;
        this.brAPIClientProvider = brAPIClientProvider;
    }

    public Optional<Program> getById(UUID programId) {

        List<Program> programs = dao.get(programId);

        if (programs.size() <= 0) {
            return Optional.empty();
        }

        Program program = programs.get(0);
        BrAPIProgram brapiProgram = dao.getProgramBrAPI(program);
        program.setBrAPIProperties(brapiProgram);

        return Optional.of(program);
    }

    public List<Program> getAll(AuthenticatedUser actingUser){
        /* Get all of the programs the user has access to */
        List<UUID> enrolledProgramIds = securityService.getEnrolledProgramIds(actingUser);

        List<ProgramEntity> programEntities = dao.fetchById(enrolledProgramIds.toArray(UUID[]::new))
                .stream().filter(ProgramEntity::getActive).collect(Collectors.toList());
        List<Program> programs = dao.getFromEntity(programEntities);

        return programs;
    }

    public Program create(ProgramRequest programRequest, AuthenticatedUser actingUser) throws AlreadyExistsException, UnprocessableEntityException {
        /* Create a program from a request object */

        //Check that key present
        if (programRequest.getKey().isBlank()){
            throw new UnprocessableEntityException("Program key required");
        }

        //Check that program name not already in use
        if (programNameInUse(programRequest.getName())) {
            throw new AlreadyExistsException(PROGRAM_NAME_IN_USE);
        }

        // Check that our species exists
        SpeciesRequest speciesRequest = programRequest.getSpecies();
        if (!speciesService.exists(speciesRequest.getId())){
            throw new UnprocessableEntityException("Species does not exist");
        }

        //Check that program key not already in use
        if (programKeyInUse(programRequest.getKey())) {
            throw new AlreadyExistsException(PROGRAM_KEY_IN_USE);
        }

        //Ensure program key uppercase
        programRequest.setKey(programRequest.getKey().toUpperCase());

        //Check that program key formatting correct
        ArrayList<String> keyErrors = getKeyValidationErrors(programRequest.getKey());
        if (!(keyErrors.isEmpty())) {
            throw new UnprocessableEntityException(String.join(" .", keyErrors));
        }

        String brapiUrl = programRequest.getBrapiUrl();

        // if specified, check if brapi url is supported
        if (!StringUtils.isBlank(brapiUrl)) {
            if (!dao.brapiUrlSupported(brapiUrl)) {
                throw new UnprocessableEntityException("Unsupported BrAPI URL");
            }
            // need to refresh client url for brapi program creation below
            brAPIClientProvider.setCoreClient(brapiUrl);
            brAPIClientProvider.setPhenoClient(brapiUrl);
            brAPIClientProvider.setGenoClient(brapiUrl);
        }

        Program program = dsl.transactionResult(configuration -> {

            // Create germplasm sequence
            String germplasm_sequence_name = String.format(GERMPLASM_SEQUENCE_TEMPLATE, programRequest.getKey()).toLowerCase();
            dsl.createSequence(germplasm_sequence_name).execute();

            // Create experiment sequence
            String exp_sequence_name = String.format(EXP_SEQUENCE_TEMPLATE, programRequest.getKey()).toLowerCase();
            dsl.createSequence(exp_sequence_name).execute();

            // Create obs unit sequence
            String obs_unit_sequence_name = String.format(OBS_UNIT_SEQUENCE_TEMPLATE, programRequest.getKey()).toLowerCase();
            dsl.createSequence(obs_unit_sequence_name).execute();

            // Create env sequence
            String env_sequence_name = String.format(ENV_SEQUENCE_TEMPLATE, programRequest.getKey()).toLowerCase();
            dsl.createSequence(env_sequence_name).execute();

            // Parse and create the program object
            ProgramEntity programEntity = ProgramEntity.builder()
                    .name(programRequest.getName())
                    .speciesId(speciesRequest.getId())
                    .abbreviation(programRequest.getAbbreviation())
                    .objective(programRequest.getObjective())
                    .documentationUrl(programRequest.getDocumentationUrl())
                    .brapiUrl(brapiUrl)
                    .key(programRequest.getKey())
                    .germplasmSequence(germplasm_sequence_name)
                    .expSequence( exp_sequence_name )
                    .envSequence( env_sequence_name )
                    .createdBy(actingUser.getId())
                    .updatedBy(actingUser.getId())
                    .build();

            // Insert and update
            dao.insert(programEntity);
            Program createdProgram = dao.get(programEntity.getId()).get(0);

            ProgramOntologyEntity programOntologyEntity = ProgramOntology.builder()
                    .programId(programEntity.getId())
                    .createdBy(actingUser.getId())
                    .updatedBy(actingUser.getId())
                    .build();
            programOntologyDAO.insert(programOntologyEntity);

            // Add program to brapi service
            dao.createProgramBrAPI(createdProgram);

            //TODO: Ontology to brapi services once supported by brapi
            //TODO: Add species to brapi service if it doesn't exist

            return createdProgram;
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

        // Update program in brapi service
        dao.updateProgramBrAPI(program);

        //TODO: Add species to brapi service if it doesn't exist

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

    public ProgramBrAPIEndpoints getBrapiEndpoints(UUID programId) throws DoesNotExistException {

        ProgramEntity programEntity = dao.fetchOneById(programId);
        if (programEntity == null){
            throw new DoesNotExistException("Program does not exist");
        }

        return dao.getProgramBrAPIEndpoints(programId);
    }

    private boolean programNameInUse(String name) {
        List<Program> existingPrograms = dao.getProgramByName(name, true);
        if (!existingPrograms.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    private boolean programKeyInUse(String key) {
        List<Program> existingPrograms = dao.getProgramByKey(key);
        return !existingPrograms.isEmpty();
    }

    public ArrayList getKeyValidationErrors(String key) {
        ArrayList<String> keyErrors = new ArrayList<>();
        if (key.length() < 2) {
            keyErrors.add("Key must be at least 2 characters.");
        }
        if (key.length() > 6) {
            keyErrors.add("Key must be at maximum 6 characters.");
        }
        if (!(key.matches("^[A-Z]*$"))) {
            keyErrors.add("Key must use only alphabetic characters.");
        }
        return keyErrors;
    }


}
