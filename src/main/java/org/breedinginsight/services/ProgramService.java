package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.daos.ProgramUserRoleDao;
import org.breedinginsight.dao.db.tables.daos.RoleDao;
import org.breedinginsight.dao.db.tables.pojos.*;
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
import java.lang.reflect.Array;
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

    public Program getById(UUID programId) throws DoesNotExistException {
        /* Get Program by program ID */
        List<Program> programs = dao.get(programId);

        if (programs.size() <= 0) {
            throw new DoesNotExistException("Id not associated with a program");
        }

        return programs.get(0);
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

        List<ProgramEntity> programEntities = dao.fetchByActive(true);
        List<Program> programs = dao.getFromEntity(programEntities);

        return programs;
    }

    public Program create(ProgramRequest programRequest, User actingUser) throws DoesNotExistException {
        /* Create a program from a request object */

        // Check that our species exists
        SpeciesRequest speciesRequest = programRequest.getSpecies();
        Optional<SpeciesEntity> speciesEntity = speciesService.getByIdOptional(speciesRequest.getId());
        if (speciesEntity.isEmpty()){
            throw new DoesNotExistException("Species does not exist");
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

    public Program update(UUID programId, ProgramRequest programRequest, User actingUser) throws DoesNotExistException {
        /* Update an existing program */

        ProgramEntity programEntity = dao.fetchOneById(programId);
        if (programEntity == null){
            throw new DoesNotExistException("Program does not exist");
        }

        // Check that our species exists
        SpeciesRequest speciesRequest = programRequest.getSpecies();
        Optional<SpeciesEntity> speciesEntity = speciesService.getByIdOptional(speciesRequest.getId());
        if (speciesEntity.isEmpty()){
            throw new DoesNotExistException("Species does not exist");
        }

        // Parse and create the program object
        programEntity.setName(programRequest.getName());
        programEntity.setSpeciesId(speciesRequest.getId());
        programEntity.setAbbreviation(programRequest.getAbbreviation());
        programEntity.setObjective(programRequest.getObjective());
        programEntity.setDocumentationUrl(programRequest.getDocumentationUrl());
        programEntity.setUpdatedAtUtc(OffsetDateTime.now());
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
        programEntity.setUpdatedAtUtc(OffsetDateTime.now());
        dao.update(programEntity);
    }

    public void delete(UUID programId) throws DoesNotExistException {
        /* Deletes an existing program */

        ProgramEntity programEntity = dao.fetchOneById(programId);
        if (programEntity == null){
            throw new DoesNotExistException("Program does not exist");
        }

        dao.delete(programEntity);
    }

    public List<User> getProgramUsers(UUID programId) throws DoesNotExistException {
        /* Get all of the users in the program */
        //TODO


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

        List<RoleEntity> roles = roleService.getRolesByIds(programUserRequest.getRoleIds());
        if (roles.isEmpty()) {
            throw new DoesNotExistException("Role does not exist");
        }

        List<ProgramUserRoleEntity> programUserRoles = new ArrayList<>();

        for (RoleEntity role : roles) {
            ProgramUserRoleEntity programUser = ProgramUserRoleEntity.builder()
                    .userId(user.getId())
                    .programId(programId)
                    .roleId(role.getId())
                    .build();
            programUserRoles.add(programUser);
        }

        programUserRoleDao.insert(programUserRoles);

        return user;
    }

    public void removeProgramUser(UUID programId, UUID userId) throws DoesNotExistException {
        /* Remove a user from a program, but don't delete the user. */

        if (getByIdOptional(programId).isEmpty())
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        if (userService.getByIdOptional(userId).isEmpty())
        {
            throw new DoesNotExistException("User id does not exist");
        }

        dao.deleteProgramUserRoles(programId, userId);
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
