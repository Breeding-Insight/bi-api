package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
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

@Slf4j
@Singleton
public class UserService {

    @Inject
    private UserDAO dao;

    public User getByOrcid(String orcid) throws DoesNotExistException {

        // User has been authenticated against orcid, check they have a bi account.
        List<BiUserEntity> users = dao.fetchByOrcid(orcid);

        if (users.size() != 1) {
            throw new DoesNotExistException("ORCID not associated with registered user");
        }

        // For now, if we have found a record, let them through
        return new User(users.get(0));
    }

    public List<User> getAll() {

        // Get our users
        List<BiUserEntity> users = dao.findAll();

        List<User> resultBody = new ArrayList<>();
        for (BiUserEntity queriedUser : users) {
            // We don't have roles right now
            List<String> roles = new ArrayList<>();
            // Generate our response class from db record
            User user = new User(queriedUser);

            resultBody.add(user);
        }

        return resultBody;
    }

    public Optional<User> getById(UUID userId) {

        // User has been authenticated against orcid, check they have a bi account.
        BiUserEntity biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            return Optional.empty();
        }

        return Optional.of(new User(biUser));
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
        if (existingUsers.size() > 0) {
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
}
