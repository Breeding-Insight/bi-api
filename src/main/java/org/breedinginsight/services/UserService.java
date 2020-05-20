package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.SystemRolesRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.daos.SystemRoleDao;
import org.breedinginsight.dao.db.tables.daos.SystemUserRoleDao;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.SystemRoleEntity;
import org.breedinginsight.dao.db.tables.pojos.SystemUserRoleEntity;
import org.breedinginsight.daos.ProgramUserDAO;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.*;
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
    private ProgramUserDAO programUserDAO;
    @Inject
    private DSLContext dsl;

    public Optional<User> getByOrcid(String orcid) {

        // User has been authenticated against orcid, check they have a bi account.
        Optional<User> users = dao.getUserByOrcId(orcid);

        if (users.isEmpty()) {
            return Optional.empty();
        } else {
            User newUser = users.get();
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

    public User create(AuthenticatedUser actingUser, UserRequest userRequest) throws AlreadyExistsException, UnprocessableEntityException {
        return create(actingUser, userRequest, dsl.configuration());
    }

    public User create(AuthenticatedUser actingUser, UserRequest userRequest, Configuration dslConfiguration) throws AlreadyExistsException, UnprocessableEntityException {

        try {
            User user = DSL.using(dslConfiguration).transactionResult(configuration -> {

                Optional<User> created = createOptional(actingUser, userRequest);

                if (created.isEmpty()) {
                    throw new AlreadyExistsException("Email already exists");
                }

                User newUser = created.get();
                // Check that roles are valid
                if ( userRequest.getSystemRoles() != null){
                    List<SystemRole> systemRoles = validateAndGetSystemRoles(userRequest.getSystemRoles());
                    // Update roles
                    insertSystemRoles(actingUser, newUser.getId(), systemRoles);
                }


                return getById(newUser.getId()).get();
            });

            return user;

        } catch(DataAccessException e) {
            if (e.getCause() instanceof AlreadyExistsException) {
                throw (AlreadyExistsException) e.getCause();
            } else if (e.getCause() instanceof UnprocessableEntityException) {
                throw (UnprocessableEntityException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    private Optional<User> createOptional(AuthenticatedUser actingUser, UserRequest user) {

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


    public User update(AuthenticatedUser actingUser, UUID userId, UserRequest userRequest) throws DoesNotExistException, AlreadyExistsException {

        BiUserEntity biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        if (userEmailInUseExcludingUser(userRequest.getEmail(), userId)) {
            throw new AlreadyExistsException("Email already exists");
        }

        biUser.setEmail(userRequest.getEmail());
        biUser.setName(userRequest.getName());
        biUser.setCreatedBy(actingUser.getId());
        biUser.setUpdatedBy(actingUser.getId());
        dao.update(biUser);

        return getById(userId).get();
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

    public void archive(UUID userId) throws DoesNotExistException, UnprocessableEntityException {

        BiUserEntity biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        // Check the user is not active in any programs
        List<ProgramUser> programUsers = programUserDAO.getProgramUsersByUserId(userId);
        if (programUsers.size() > 0){
            throw new UnprocessableEntityException("User is active in programs. Cannot archive.");
        }

        biUser.setActive(false);
        dao.update(biUser);
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

    private void insertSystemRoles(AuthenticatedUser actingUser, UUID userId, List<SystemRole> systemRoles) {

        List<SystemUserRoleEntity> newSystemUserRoles = new ArrayList<>();
        for (SystemRole systemRoleEntity : systemRoles) {
            SystemUserRoleEntity systemUserRoleEntity = SystemUserRoleEntity.builder()
                    .biUserId(userId)
                    .systemRoleId(systemRoleEntity.getId())
                    .createdBy(actingUser.getId())
                    .updatedBy(actingUser.getId())
                    .build();
            newSystemUserRoles.add(systemUserRoleEntity);
        }

        if (!newSystemUserRoles.isEmpty()) {
            systemUserRoleDao.insert(newSystemUserRoles.toArray(new SystemUserRoleEntity[newSystemUserRoles.size()]));
        }

    }

    public User updateRoles(AuthenticatedUser actingUser, UUID userId, SystemRolesRequest systemRolesRequest)
            throws DoesNotExistException, AuthorizationException, UnprocessableEntityException {

        BiUserEntity biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        if (actingUser.getId().toString().equals(userId.toString())){
            throw new AuthorizationException("User cannot update own roles.");
        }

        List<SystemRole> systemRoles = validateAndGetSystemRoles(systemRolesRequest.getSystemRoles());

        try {
            User user = dsl.transactionResult(configuration -> {
                deleteSystemRoles(userId);
                insertSystemRoles(actingUser, userId, systemRoles);
                return getById(userId).get();
            });
            return user;
        } catch(DataAccessException e) {
            if (e.getCause() instanceof DoesNotExistException) {
                throw (DoesNotExistException) e.getCause();
            } else {
                throw e;
            }
        }
    }

    private List<SystemRole> validateAndGetSystemRoles(List<SystemRole> systemRoles) throws UnprocessableEntityException {

        Set<UUID> roleIds = systemRoles.stream().map(role -> role.getId()).collect(Collectors.toSet());

        List<SystemRoleEntity> roles = systemRoleDao.fetchById(roleIds.toArray(new UUID[roleIds.size()]));
        if (roles.size() != roleIds.size()) {
            throw new UnprocessableEntityException("Role does not exist");
        }

        List<SystemRole> queriedSystemRoles = roles.stream().map(role -> new SystemRole(role)).collect(Collectors.toList());

        return queriedSystemRoles;
    }

    public boolean exists(UUID id){
        return dao.existsById(id);
    }
}
