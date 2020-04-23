package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.dao.db.tables.pojos.*;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.*;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class ProgramService {

    @Inject
    private ProgramDAO dao;
    @Inject
    private SpeciesService speciesService;

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

    public Program create(ProgramRequest programRequest, User actingUser) throws UnprocessableEntityException {
        /* Create a program from a request object */

        // Check that our species exists
        SpeciesRequest speciesRequest = programRequest.getSpecies();
        if (!speciesService.exists(speciesRequest.getId())){
            throw new UnprocessableEntityException("Species does not exist");
        }

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
        Program program = dao.get(programEntity.getId()).get(0);

        return program;
    }

    public Program update(UUID programId, ProgramRequest programRequest, User actingUser) throws DoesNotExistException, UnprocessableEntityException {
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

    public void archive(UUID programId, User actingUser) throws DoesNotExistException {
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


}
