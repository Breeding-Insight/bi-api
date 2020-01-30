package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.api.model.v1.response.UserInfoResponse;
import org.breedinginsight.dao.db.tables.daos.BiUserDao;
import org.breedinginsight.dao.db.tables.pojos.JooqBiUser;
import org.breedinginsight.dao.db.tables.records.BiUserRecord;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.jooq.DAO;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.BI_USER;

@Slf4j
@Singleton
public class UserService {

    @Inject
    private DSLContext dsl;

    //@Inject
    //private DAO dao;

    public UserInfoResponse get(String orcid) throws DoesNotExistException {

        // User has been authenticated against orcid, check they have a bi account.
        BiUserRecord result = dsl.fetchOne(BI_USER, BI_USER.ORCID.eq(orcid));

        if (result == null) {
            throw new DoesNotExistException("ORCID not associated with registered user");
        }

        // For now, if we have found a record, let them through
        return new UserInfoResponse(result);
    }

    public List<UserInfoResponse> getAll() {

        // Get our users
        List<BiUserRecord> results = dsl.select().from(BI_USER).fetchInto(BiUserRecord.class);

        List<UserInfoResponse> resultBody = new ArrayList<>();
        for (BiUserRecord result : results) {
            // We don't have roles right now
            List<String> roles = new ArrayList<>();
            // Generate our response class from db record
            UserInfoResponse userInfoResponse = new UserInfoResponse(result)
                    .setRoles(roles);

            resultBody.add(userInfoResponse);
        }

        return resultBody;
    }

    public UserInfoResponse get(UUID userId) throws DoesNotExistException {

        // User has been authenticated against orcid, check they have a bi account.
        BiUserRecord biUser = dsl.fetchOne(BI_USER, BI_USER.ID.eq(userId));

        if (biUser == null) {
            //log.info("UUID for user does not exist");
            //return HttpResponse.notFound();
            throw new DoesNotExistException("UUID for user does not exist");
        }

        return new UserInfoResponse(biUser);

    }

    public UserInfoResponse create(UserRequest user) throws MissingRequiredInfoException, AlreadyExistsException {

        // Check that name and email was provided
        if (user.getEmail() == null || user.getName() == null) {
            throw new MissingRequiredInfoException("Missing name or email");
        }

        if (userEmailInUse(user.getEmail())) {
            throw new AlreadyExistsException("Email already exists");
        }

        /*
        JooqBiUser jooqUser = new JooqBiUser();
        jooqUser.setName(user.getName());
        jooqUser.setEmail(user.getEmail());
        dao.insert(jooqUser);
        UserInfoResponse userInfoResponse = new UserInfoResponse(jooqUser);
         */

        // Create the user
        BiUserRecord newBiUser = dsl.newRecord(BI_USER)
                .setEmail(user.getEmail())
                .setName(user.getName());
        newBiUser.store();

        return new UserInfoResponse(newBiUser);
    }

    public UserInfoResponse update(UUID userId, UserRequest user) throws DoesNotExistException, AlreadyExistsException {

        // Update the user info
        BiUserRecord biUser = dsl.fetchOne(BI_USER, BI_USER.ID.eq(userId));

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

        // Store our record
        biUser.store();
        // Get our updated record
        biUser.refresh();
        // Convert to return object
        return new UserInfoResponse(biUser);
    }

    public void delete(UUID userId) throws DoesNotExistException {

        // Delete the user
        BiUserRecord biUser = dsl.fetchOne(BI_USER, BI_USER.ID.eq(userId));

        if (biUser == null) {
            throw new DoesNotExistException("UUID for user does not exist");
        }

        biUser.delete();
    }

    private boolean userEmailInUse(String email) {
        // Check if the users email already exists
        Integer numExistEmails = dsl.selectCount()
                .from(BI_USER)
                .where(BI_USER.EMAIL.eq(email))
                .fetchOne(0, Integer.class);

        // Check if the email is already in use
        if (numExistEmails > 0) { return true; }
        else { return false; }
    }

    private boolean userEmailInUseExcludingUser(String email, UUID userId) {
        // Check if the users email already exists
        Integer numExistEmails = dsl.selectCount()
                .from(BI_USER)
                .where(BI_USER.EMAIL.eq(email).and(BI_USER.ID.ne(userId)))
                .fetchOne(0, Integer.class);

        // Check if the email is already in use
        if (numExistEmails > 0) { return true; }
        else { return false; }
    }
}
