package org.breedinginsight.services;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;

import javax.inject.Inject;

import static org.breedinginsight.dao.db.Tables.BI_USER;

@Slf4j
@AllArgsConstructor
public class UserService {

    DSLContext dsl;

    public boolean userEmailInUse(String email) {

        // Check if the users email already exists
        Integer numExistEmails = dsl.selectCount().from(BI_USER).where(BI_USER.EMAIL.eq(email)).fetchOne(0, Integer.class);

        // Check if the email is already in use
        if (numExistEmails > 0) { return true; }
        else { return false; }

    }
}
