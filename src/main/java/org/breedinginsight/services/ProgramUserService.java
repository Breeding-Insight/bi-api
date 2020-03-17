package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.pojos.ProgramUserRoleEntity;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.daos.ProgramUserDao;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.Role;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

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
    @Inject
    private DSLContext dsl;

    public ProgramUser addProgramUser(User actingUser, UUID programId, ProgramUserRequest programUserRequest) throws DoesNotExistException, AlreadyExistsException, UnprocessableEntityException {
        /* Add a user to a program. Create the user if they don't exist. */

        try {
            ProgramUser programUser = dsl.transactionResult(configuration -> {

                if (!programService.exists(programId)) {
                    throw new DoesNotExistException("Program id does not exist");
                }

                List<Role> roles = validateAndGetRoles(programUserRequest);

                User user;
                Optional<User> optUser = userService.getByIdOptional(programUserRequest.getUser().getId());

                if (optUser.isPresent()) {
                    user = optUser.get();

                    // check if user is already in program, only allow puts for update, not posts
                    Optional<ProgramUser> existingProgramUser = getProgramUserbyIdOptional(programId, user.getId());

                    if (existingProgramUser.isPresent()) {
                        throw new AlreadyExistsException("Program user already exists");
                    }
                } else {

                    // if an id was specified but not found in the system
                    if (programUserRequest.getUser().getId() != null) {
                        throw new UnprocessableEntityException("User Id Not Found");
                    }

                    // user doesn't exist so create them
                    UserRequest userRequest = new UserRequest().builder()
                            .name(programUserRequest.getUser().getName())
                            .email(programUserRequest.getUser().getEmail())
                            .build();
                    optUser = userService.createOptional(userRequest);
                    if (optUser.isEmpty()) {
                        throw new AlreadyExistsException("Cannot create new user, email already exists");
                    } else {
                        user = optUser.get();
                    }
                }

                return updateProgramUser(actingUser, programId, user.getId(), roles);
            });

            return programUser;

        } catch(DataAccessException e) {
            if (e.getCause() instanceof AlreadyExistsException) {
                throw (AlreadyExistsException)e.getCause();
            }
            else if (e.getCause() instanceof DoesNotExistException) {
                throw (DoesNotExistException)e.getCause();
            }
            else if (e.getCause() instanceof UnprocessableEntityException) {
                throw (UnprocessableEntityException) e.getCause();
            }
            else {
                throw e;
            }
        }
    }

    private List<Role> validateAndGetRoles(ProgramUserRequest programUserRequest) throws AlreadyExistsException, DoesNotExistException {

        List<UUID> roleIds = programUserRequest.getRoles().stream().map(role -> role.getId()).collect(Collectors.toList());

        if (hasDuplicatesRoleIds(roleIds)) {
            throw new AlreadyExistsException("Duplicate roles specified, must be unique");
        }

        List<Role> roles = roleService.getRolesByIds(roleIds);
        if (roles.isEmpty() || roles.size() != roleIds.size()) {
            throw new DoesNotExistException("Role does not exist");
        }

        return roles;
    }

    private boolean hasDuplicatesRoleIds(List<UUID> roles) {
        Set<UUID> set = new HashSet<>();
        for (UUID role: roles){
            if (!set.add(role))
                return true;
        }
        return false;
    }

    private ProgramUser updateProgramUser(User actingUser, UUID programId, UUID userId, List<Role> roles) throws DoesNotExistException {

        List<ProgramUserRoleEntity> programUserRoles = new ArrayList<>();

        for (RoleEntity role : roles) {
            ProgramUserRoleEntity programUser = ProgramUserRoleEntity.builder()
                    .userId(userId)
                    .programId(programId)
                    .roleId(role.getId())
                    .createdBy(actingUser.getId())
                    .updatedBy(actingUser.getId())
                    .build();
            programUserRoles.add(programUser);
        }

        // insert
        programUserDao.insert(programUserRoles);

        ProgramUser programUser = getProgramUserbyId(programId, userId);

        return programUser;
    }

    public ProgramUser editProgramUser(User actingUser, UUID programId, ProgramUserRequest programUserRequest) throws DoesNotExistException, AlreadyExistsException {

        try {
            ProgramUser programUser = dsl.transactionResult(configuration -> {

                if (!programService.exists(programId)) {
                    throw new DoesNotExistException("Program id does not exist");
                }

                User user;
                List<Role> roles = validateAndGetRoles(programUserRequest);

                Optional<User> optUser = userService.getByIdOptional(programUserRequest.getUser().getId());

                if (optUser.isPresent()) {
                    user = optUser.get();
                } else {
                    throw new DoesNotExistException("User id does not exist");
                }

                // check if user is already in program, only allow puts for update, no posts
                Optional<ProgramUser> existingProgramUser = getProgramUserbyIdOptional(programId, user.getId());

                if (existingProgramUser.isEmpty()) {
                    throw new DoesNotExistException("Program user does not exist");
                }

                // delete existing roles and replace with new roles
                programUserDao.deleteProgramUserRoles(programId, user.getId());
                return updateProgramUser(actingUser, programId, user.getId(), roles);
            });

            return programUser;
        } catch(DataAccessException e) {
            if (e.getCause() instanceof AlreadyExistsException) {
                throw (AlreadyExistsException)e.getCause();
            }
            else if (e.getCause() instanceof DoesNotExistException) {
                throw (DoesNotExistException)e.getCause();
            }
            else {
                throw e;
            }
        }
    }

    public void removeProgramUser(UUID programId, UUID userId) throws DoesNotExistException {
        /* Remove a user from a program, but don't delete the user. */

        if (!programService.exists(programId))
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
        return programUserDao.getProgramUsers(programId);
    }

    public ProgramUser getProgramUserbyId(UUID programId, UUID userId) throws DoesNotExistException {
        Optional<ProgramUser> user = getProgramUserbyIdOptional(programId, userId);

        if (user.isEmpty()) {
            throw new DoesNotExistException("Program user does not exist");
        }

        return user.get();
    }

    public Optional<ProgramUser> getProgramUserbyIdOptional(UUID programId, UUID userId) {
        /* Get a program user by their id */
        ProgramUser user = programUserDao.getProgramUser(programId, userId);

        if (user == null) {
            return Optional.empty();
        }

        return Optional.of(user);
    }

}
