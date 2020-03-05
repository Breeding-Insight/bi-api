package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.daos.ProgramUserRoleDao;
import org.breedinginsight.dao.db.tables.daos.RoleDao;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.dao.db.tables.pojos.ProgramUserRoleEntity;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.dao.db.tables.ProgramTable;
import org.breedinginsight.dao.db.tables.records.ProgramRecord;
import org.breedinginsight.daos.ProgramDao;
import org.breedinginsight.model.Location;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.tools.StringUtils;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

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
    private ProgramDao dao;
    @Inject
    private SpeciesService speciesService;

    @Inject
    private ProgramUserRoleDao programUserRoleDao;

    @Inject
    private UserService userService;

    @Inject
    private RoleService roleService;

    public ProgramEntity getById(UUID programId) throws DoesNotExistException {
        /* Get Program by program ID */
        Optional<ProgramEntity> program = getByIdOptional(programId);

        if (program.isEmpty()) {
            throw new DoesNotExistException("Id not associated with a program");
        }

        return program.get();
    }

    public Optional<ProgramEntity> getByIdOptional(UUID programId) {

        ProgramEntity program = dao.fetchOneById(programId);

        if (program == null) {
            return Optional.empty();
        }

        return Optional.of(program);
    }

    public List<Program> getAll(){
        /* Get all of the programs */

        List<Program> programs = dao.getAll();

        return programs;
    }

    public ProgramEntity create(ProgramRequest programRequest) throws DoesNotExistException {
        /* Create a program from a request object */

        // Check that our species exists
        SpeciesRequest speciesRequest = programRequest.getSpecies();
        try {
            speciesService.getById(speciesRequest);
        } catch (DoesNotExistException e){
            throw new DoesNotExistException(e.toString());
        }

        // Parse and create the program object
        ProgramEntity programEntity = ProgramEntity.builder()
                .name(programRequest.getName())
                .speciesId(speciesRequest.getId())
                .abbreviation(programRequest.getAbbreviation())
                .objective(programRequest.getObjective())
                .documentationUrl(programRequest.getDocumentationUrl())
                .build();

        // Insert and update
        programEntity = dao.insertThenFetch(programEntity);

        return programEntity;
    }

    public ProgramEntity update(UUID programId, ProgramRequest programRequest) throws DoesNotExistException {
        /* Update an existing program */

        ProgramEntity programEntity = dao.fetchOneById(programId);
        if (programEntity == null){
            throw new DoesNotExistException("Program does not exist");
        }

        // Check that the species exists
        SpeciesRequest speciesRequest = programRequest.getSpecies();
        try {
            speciesService.getById(speciesRequest);
        } catch (DoesNotExistException e){
            throw new DoesNotExistException(e.toString());
        }

        // Parse and create the program object
        programEntity.setName(programRequest.getName());
        programEntity.setSpeciesId(speciesRequest.getId());
        programEntity.setAbbreviation(programRequest.getAbbreviation());
        programEntity.setObjective(programRequest.getObjective());
        programEntity.setDocumentationUrl(programRequest.getDocumentationUrl());
        programEntity.setUpdatedAtUtc(OffsetDateTime.now());

        dao.update(programEntity);

        return programEntity;
    }

    public void archive(UUID programId) throws DoesNotExistException {
        /* Archive an existing program */
        //TODO
    }

    public List<User> getProgramUsers(UUID programId) throws DoesNotExistException {
        /* Get all of the users in the program */
        //TODO
        //List<ProgramUserRoleEntity> users = programUserRoleDao.fetchByProgramId(programId);


        return new ArrayList<>();
    }

    public User getProgramUserbyId(UUID programId, UUID userId) throws DoesNotExistException {
        /* Get a program user by their id */
        //TODO
        return null;
    }

    public User addProgramUser(UUID programId, ProgramUserRequest programUserRequest) throws DoesNotExistException, AlreadyExistsException {
        /* Add a user to a program. Create the user if they don't exist. */
        User user;

        //TODO: Jooq transaction stuff maybe

        if (getByIdOptional(programId).isEmpty())
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        Optional<User> optUser = userService.getByIdOptional(programUserRequest.getId());

        if (optUser.isPresent()) {
            user = optUser.get();
        }
        else {
            // user doesn't exist so create them
            UserRequest userRequest = new UserRequest().builder()
                    .name(programUserRequest.getName())
                    .email(programUserRequest.getEmail())
                    .build();
            optUser = userService.createOptional(userRequest);
            if (optUser.isEmpty()) {
                throw new AlreadyExistsException("Cannot create new user, email already exists");
            } else {
                user = optUser.get();
            }
        }

        Optional<RoleEntity> role = roleService.getByIdOptional(programUserRequest.getRoleId());
        if (role.isEmpty()) {
            throw new DoesNotExistException("Role does not exist");
        }

        ProgramUserRoleEntity programUser = ProgramUserRoleEntity.builder()
                .userId(user.getId())
                .programId(programId)
                .roleId(role.get().getId())
                .build();

        programUserRoleDao.insert(programUser);

        return user;
    }

    public void removeProgramUser(UUID programId, UUID userId) throws DoesNotExistException {
        /* Remove a user from a program, but don't delete the user. */

        getById(programId); // throws DoesNotExist if program doesn't exist
        userService.getById(userId); // throws DoesNotExist if user doesn't exist

        // TODO: need to know what role unless they can only have one
        ProgramUserRoleEntity entity = ProgramUserRoleEntity.builder().programId(programId).userId(userId).build();

        programUserRoleDao.delete(entity);
    }

    public List<Location> getProgramLocations(UUID programId) throws DoesNotExistException {
        /* Get the locations associated with a program. */
        //TODO
        return new ArrayList<>();
    }

    public Location getProgramLocation(UUID programId, UUID locationId) throws DoesNotExistException {
        /* Get a specific location for a program. */
        //TODO
        return null;
    }

    public Location addProgramLocation(UUID programId, ProgramLocationRequest programLocationRequest) throws DoesNotExistException, AlreadyExistsException {
        /* Add a location to a program. */
        //TODO
        return null;
    }

    public void removeProgramLocation(UUID programId, UUID locationId) throws DoesNotExistException {
        /* Removes a location from a program. Does not delete the location object. */
        //TODO
    }
}
