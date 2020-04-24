package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.daos.SystemRoleDao;
import org.breedinginsight.dao.db.tables.daos.SystemUserRoleDao;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.SystemRoleEntity;
import org.breedinginsight.dao.db.tables.pojos.SystemUserRoleEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.Role;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class UserService {

    @Inject
    private UserDAO dao;
    @Inject
    private SystemUserRoleDao systemUserRoleDao;
    @Inject
    private SystemRoleDao systemRoleDao;
    @Inject
    private DSLContext dsl;

    public Optional<User> getByOrcid(String orcid) {

        // User has been authenticated against orcid, check they have a bi account.
        Optional<User> users = dao.getUserByOrcId(orcid);

        if (users.isEmpty()) {
            return Optional.empty();
        } else {
            User newUser = new User(users.get(0));
            return Optional.of(newUser);
        }
    }

    public List<User> getAll() {

        // Get our users
        List<User> users = dao.getUsers();
        return users;
    }

    public Optional<User> getById(UUID userId) {

        // User has been authenticated against orcid, check they have a bi account.
        Optional<User> user = dao.getUser(userId);

        if (!user.isPresent()) {
            return Optional.empty();
        }

        return user;
    }

    public User create(User actingUser, UserRequest userRequest) throws AlreadyExistsException, DoesNotExistException {

        try {
            User user = dsl.transactionResult(configuration -> {

                Optional<User> created = createOptional(actingUser, userRequest);

                if (created.isEmpty()) {
                    throw new AlreadyExistsException("Email already exists");
                }

                // Check that roles are valid
                List<SystemRole> systemRoles = validateAndGetSystemRoles(userRequest);

                // Update roles
                User newUser = created.get();
                deleteSystemRoles(newUser.getId());
                insertSystemRoles(actingUser.getId(), newUser.getId(), systemRoles);

                return getById(newUser.getId()).get();
            });

            return user;

        } catch(DataAccessException e) {
            if (e.getCause() instanceof AlreadyExistsException) {
                throw (AlreadyExistsException) e.getCause();
            } else if (e.getCause() instanceof DoesNotExistException) {
                throw (DoesNotExistException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    private Optional<User> createOptional(User actingUser, UserRequest user) {

        if (userEmailInUse(user.getEmail())) {
            return Optional.empty();
        }

        BiUserEntity jooqUser = new BiUserEntity();
        jooqUser.setName(user.getName());
        jooqUser.setEmail(user.getEmail());
        jooqUser.setCreatedBy(actingUser.getId());
        jooqUser.setUpdatedBy(actingUser.getId());
        dao.insert(jooqUser);
        return Optional.of(new User(jooqUser));
    }


    public User update(User actingUser, UUID userId, UserRequest userRequest) throws DoesNotExistException, AlreadyExistsException {

        BiUserEntity biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        if (userEmailInUseExcludingUser(userRequest.getEmail(), userId)) {
            throw new AlreadyExistsException("Email already exists");
        }

        List<SystemRole> systemRoles = validateAndGetSystemRoles(userRequest);

        User user = dsl.transactionResult(configuration -> {
            biUser.setEmail(userRequest.getEmail());
            biUser.setName(userRequest.getName());
            biUser.setCreatedBy(actingUser.getId());
            biUser.setUpdatedBy(actingUser.getId());

            dao.update(biUser);

            // Update system roles. User can't modify their own roles.
            if (!actingUser.getId().toString().equals(userId.toString())) {
                deleteSystemRoles(userId);
                insertSystemRoles(actingUser.getId(), userId, systemRoles);
            }

            return getById(userId).get();
        });

        return user;
    }

    public void delete(UUID userId) throws DoesNotExistException {

        BiUserEntity biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        dsl.transaction(configuration -> {
            deleteSystemRoles(userId);
            dao.deleteById(userId);
        });
    }

    private boolean userEmailInUse(String email) {

        List<BiUserEntity> existingUsers = dao.fetchByEmail(email);
        if (!existingUsers.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    private boolean userEmailInUseExcludingUser(String email, UUID userId) {

        List<BiUserEntity> existingUsers = dao.fetchByEmail(email);
        for (BiUserEntity user : existingUsers) {
            if (!user.getId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    private void deleteSystemRoles(UUID userId) {
        List<SystemUserRoleEntity> currentSystemRoles = systemUserRoleDao.fetchByBiUserId(userId);
        systemUserRoleDao.delete(currentSystemRoles);
    }

    private void insertSystemRoles(UUID actingUserId, UUID userId, List<SystemRole> systemRoles) {

        List<SystemUserRoleEntity> newSystemUserRoles = new ArrayList<>();
        for (SystemRole systemRoleEntity : systemRoles) {
            SystemUserRoleEntity systemUserRoleEntity = SystemUserRoleEntity.builder()
                    .biUserId(userId)
                    .systemRoleId(systemRoleEntity.getId())
                    .createdBy(actingUserId)
                    .updatedBy(actingUserId)
                    .build();
            newSystemUserRoles.add(systemUserRoleEntity);
        }

        if (!newSystemUserRoles.isEmpty()) {
            systemUserRoleDao.insert(newSystemUserRoles);
        }

    }

    private List<SystemRole> validateAndGetSystemRoles(UserRequest userRequest) throws DoesNotExistException {

        Set<UUID> roleIds = userRequest.getSystemRoles().stream().map(role -> role.getId()).collect(Collectors.toSet());

        List<SystemRoleEntity> roles = systemRoleDao.fetchById(roleIds.toArray(new UUID[roleIds.size()]));
        if (roles.size() != roleIds.size()) {
            throw new DoesNotExistException("Role does not exist");
        }

        List<SystemRole> systemRoles = roles.stream().map(role -> new SystemRole(role)).collect(Collectors.toList());

        return systemRoles;
    }
}
