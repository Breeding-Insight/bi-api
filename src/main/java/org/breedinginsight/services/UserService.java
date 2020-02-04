package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.dao.db.tables.pojos.BiUser;
import org.breedinginsight.daos.UserDao;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class UserService {

    @Inject
    private UserDao dao;

    public User getByOrcid(String orcid) throws DoesNotExistException {

        // User has been authenticated against orcid, check they have a bi account.
        List<BiUser> users = dao.fetchByOrcid(orcid);

        if (users.size() != 1) {
            throw new DoesNotExistException("ORCID not associated with registered user");
        }

        // For now, if we have found a record, let them through
        return new User(users.get(0));
    }

    public List<User> getAll() {

        // Get our users
        List<BiUser> users = dao.findAll();

        List<User> resultBody = new ArrayList<>();
        for (BiUser queriedUser : users) {
            // We don't have roles right now
            List<String> roles = new ArrayList<>();
            // Generate our response class from db record
            User user = new User(queriedUser)
                    .setRoles(roles);

            resultBody.add(user);
        }

        return resultBody;
    }

    public User getById(UUID userId) throws DoesNotExistException {

        // User has been authenticated against orcid, check they have a bi account.
        BiUser biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        return new User(biUser);
    }

    public User create(UserRequest user) throws MissingRequiredInfoException, AlreadyExistsException {

        // Check that name and email was provided
        if (user.getEmail() == null || user.getName() == null) {
            throw new MissingRequiredInfoException("Missing name or email");
        }

        if (userEmailInUse(user.getEmail())) {
            throw new AlreadyExistsException("Email already exists");
        }

        BiUser jooqUser = new BiUser();
        jooqUser.setName(user.getName());
        jooqUser.setEmail(user.getEmail());
        dao.insert(jooqUser);
        return new User(jooqUser);
    }

    public User update(UUID userId, UserRequest user) throws DoesNotExistException, AlreadyExistsException {

        BiUser biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        // If values are specified, update them
        if (user.getEmail() != null) {
            // Return a conflict with an 'account already exists' flag and message
            if (userEmailInUseExcludingUser(user.getEmail(), userId)) {
                throw new AlreadyExistsException("Email already exists");
            }
            biUser.setEmail(user.getEmail());
        }

        if (user.getName() != null) {
            biUser.setName(user.getName());
        }

        dao.update(biUser);

        return new User(biUser);
    }

    public void delete(UUID userId) throws DoesNotExistException {

        BiUser biUser = dao.fetchOneById(userId);

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        dao.deleteById(userId);
    }

    private boolean userEmailInUse(String email) {

        List<BiUser> existingUsers = dao.fetchByEmail(email);
        if (existingUsers.size() > 0) {
            return true;
        } else {
            return false;
        }
    }

    private boolean userEmailInUseExcludingUser(String email, UUID userId) {

        List<BiUser> existingUsers = dao.fetchByEmail(email);
        for (BiUser user : existingUsers) {
            if (!user.getId().equals(userId)) {
                return true;
            }
        }
        return false;
    }
}
