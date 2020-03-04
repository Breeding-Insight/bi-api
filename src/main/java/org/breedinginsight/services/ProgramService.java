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
import org.breedinginsight.daos.ProgramDao;
import org.breedinginsight.model.Location;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.tools.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class ProgramService {

    @Inject
    private ProgramDao dao;

    @Inject
    private ProgramUserRoleDao programUserRoleDao;

    @Inject
    private UserService userService;

    @Inject
    private RoleService roleService;

    public ProgramEntity getById(UUID programId) throws DoesNotExistException {
        /* Get Program by program ID */

        List<ProgramEntity> programs = dao.fetchById(programId);

        if (programs.size() != 1) {
            throw new DoesNotExistException("Id not associated with a program");
        }

        ProgramEntity programEntity = programs.get(0);

        //ProgramEntity

        return programs.get(0);
    }

    public List<Program> getAll(){
        /* Get all of the programs */

        List<Program> programs = dao.getAll();

        return programs;
    }

    public ProgramEntity create(ProgramRequest programRequest) throws AlreadyExistsException {
        /* Create a program from a request object */
        // TODO
        return null;
    }

    public ProgramEntity update(UUID programId, ProgramRequest programRequest) throws DoesNotExistException {
        /* Update an existing program */
        //TODO
        return null;
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

        getById(programId); // throws DoesNotExist if program doesn't exist

        try {
            user = userService.getById(programUserRequest.getId());
        } catch (DoesNotExistException e) {
            // user doesn't exist so create them
            UserRequest userRequest = new UserRequest().builder()
                    .name(programUserRequest.getName())
                    .email(programUserRequest.getEmail())
                    .build();
            user = userService.create(userRequest); // throws AlreadyExists if duplicate user
        }

        RoleEntity role = roleService.getById(programUserRequest.getRoleId()); // throws DoesNotExist if bad role

        ProgramUserRoleEntity programUser = ProgramUserRoleEntity.builder()
                .userId(user.getId())
                .programId(programId)
                .roleId(role.getId())
                .build();

        programUserRoleDao.insert(programUser);

        return user;
    }

    public void removeProgramUser(UUID programId, UUID userId) throws DoesNotExistException {
        /* Remove a user from a program, but don't delete the user. */
        //TODO

        // builder?
        ProgramUserRoleEntity entity = new ProgramUserRoleEntity();
        entity.setProgramId(programId);
        entity.setUserId(userId);
        // role_id?

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
