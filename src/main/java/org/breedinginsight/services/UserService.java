package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;

import javax.inject.Inject;

import java.util.UUID;

import static org.breedinginsight.dao.db.Tables.BI_USER;

@Slf4j
public class UserService {

    @Inject
    private DSLContext dsl;

    public boolean userEmailInUse(String email) {

        // Check if the users email already exists
        Integer numExistEmails = dsl.selectCount()
                .from(BI_USER)
                .where(BI_USER.EMAIL.eq(email))
                .fetchOne(0, Integer.class);

        // Check if the email is already in use
        if (numExistEmails > 0) { return true; }
        else { return false; }
    }

    public boolean userEmailInUseExcludingUser(String email, UUID userId) {
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
