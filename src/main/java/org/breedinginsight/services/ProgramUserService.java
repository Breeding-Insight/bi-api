package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.daos.ProgramUserRoleDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramUserRoleEntity;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.daos.ProgramUserDao;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.Role;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class ProgramUserService {

    @Inject
    private ProgramUserDao programUserDao;
    @Inject
    private ProgramService programService;
    @Inject
    private UserService userService;
    @Inject
    private RoleService roleService;

    public User addProgramUser(UUID programId, ProgramUserRequest programUserRequest) throws DoesNotExistException, AlreadyExistsException {
        /* Add a user to a program. Create the user if they don't exist. */
        User user;

        //TODO: Jooq transaction stuff maybe

        if (programService.getByIdOptional(programId).isEmpty())
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

        List<Role> roles = roleService.getRolesByIds(programUserRequest.getRoleIds());
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

        programUserDao.insert(programUserRoles);

        return user;
    }

    public void removeProgramUser(UUID programId, UUID userId) throws DoesNotExistException {
        /* Remove a user from a program, but don't delete the user. */

        if (programService.getByIdOptional(programId).isEmpty())
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        if (userService.getByIdOptional(userId).isEmpty())
        {
            throw new DoesNotExistException("User id does not exist");
        }

        programUserDao.deleteProgramUserRoles(programId, userId);
    }

    public List<ProgramUser> getProgramUsers(UUID programId) throws DoesNotExistException {
        /* Get all of the users in the program */
        //TODO

        return new ArrayList<>();
    }

    public User getProgramUserbyId(UUID programId, UUID userId) throws DoesNotExistException {
        /* Get a program user by their id */
        //TODO
        return null;
    }

}
