package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.daos.SystemRoleDao;
import org.breedinginsight.dao.db.tables.daos.SystemUserRoleDao;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.SystemRoleEntity;
import org.breedinginsight.dao.db.tables.pojos.SystemUserRoleEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    public User create(User actingUser, UserRequest user) throws AlreadyExistsException {

        Optional<User> created = createOptional(actingUser, user);

        if (created.isEmpty()) {
            throw new AlreadyExistsException("Email already exists");
        }

        return created.get();
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


    public User update(User actingUser, UUID userId, UserRequest user) throws DoesNotExistException, AlreadyExistsException {

        BiUserEntity biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        if (userEmailInUseExcludingUser(user.getEmail(), userId)) {
            throw new AlreadyExistsException("Email already exists");
        }
        biUser.setEmail(user.getEmail());
        biUser.setName(user.getName());
        biUser.setCreatedBy(actingUser.getId());
        biUser.setUpdatedBy(actingUser.getId());

        dao.update(biUser);

        return new User(biUser);
    }

    public void delete(UUID userId) throws DoesNotExistException {

        BiUserEntity biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        dao.deleteById(userId);
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

    private List<String> getUserSystemRoles(User user) {

        List<SystemUserRoleEntity> systemUserRoleEntities = systemUserRoleDao.fetchByBiUserId(user.getId());
        if (systemUserRoleEntities.size() > 0){
            List<UUID> userRoleIds = systemUserRoleEntities.stream()
                    .map((systemUserRoleEntity) -> systemUserRoleEntity.getSystemRoleId())
                    .collect(Collectors.toList());
            List<SystemRoleEntity> systemRoleEntities = systemRoleDao.fetchById(userRoleIds.toArray(new UUID[userRoleIds.size()]));
            List<String> userRoles = systemRoleEntities.stream()
                    .map(systemRoleEntity -> systemRoleEntity.getDomain())
                    .collect(Collectors.toList());
            return userRoles;
        } else {
            return new ArrayList<>();
        }
    }
}
