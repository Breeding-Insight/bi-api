package org.breedinginsight.daos;

import io.micronaut.test.annotation.MicronautTest;
import org.breedinginsight.dao.db.tables.pojos.BiUser;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * This is testing Jooq code, not ours, tests are here to demonstrate usage and api works as expected
 */

@MicronautTest
public class DSLTransactionResultIntegrationTest {

    @Inject
    DSLContext dsl;

    @Inject
    UserDao dao;

    @Test
    void noRollback() {
        BiUser user = dsl.transactionResult(configuration -> {
            BiUser jooqUser = new BiUser();
            jooqUser.setName("Test User");
            jooqUser.setEmail("norollback@test.com");
            dao.insert(jooqUser);
            return jooqUser;
        });
        assertEquals("Test User", user.getName());
        assertEquals("norollback@test.com", user.getEmail());
        BiUser dbuser = dao.fetchOneById(user.getId());
        assertEquals(user.getId(), dbuser.getId());
        assertEquals("Test User", dbuser.getName());
        assertEquals("norollback@test.com", dbuser.getEmail());
        dao.deleteById(dbuser.getId());
    }

    @Test
    void rollback() {
        DataAccessException e = assertThrows(DataAccessException.class, () -> {
            BiUser user = dsl.transactionResult(configuration -> {
            BiUser jooqUser = new BiUser();
            jooqUser.setName("Test User");
            jooqUser.setEmail("rollback@test.com");
            dao.insert(jooqUser);
            throw new AlreadyExistsException("test");
        });
        }, "Rollback caused");

        List<BiUser> dbusers = dao.fetchByEmail("rollback@test.com");
        assertEquals(0, dbusers.size());
        assertTrue(e.getCause() instanceof AlreadyExistsException);
        assertEquals("test", e.getCause().getMessage());
    }

    @Test
    void nestedNoRollback() {

        BiUser user = dsl.transactionResult(configuration -> {

            BiUser innerUser = DSL.using(configuration).transactionResult(configuration2 -> {
                BiUser jooqUser = new BiUser();
                jooqUser.setName("Test Inner");
                jooqUser.setEmail("norollbackinner@test.com");
                dao.insert(jooqUser);
                return jooqUser;
            });

            BiUser jooqUser = new BiUser();
            jooqUser.setName("Test Outer");
            jooqUser.setEmail("norollbackouter@test.com");
            dao.insert(jooqUser);
            return jooqUser;
        });

        BiUser innerUser = dao.fetchByEmail("norollbackinner@test.com").get(0);
        assertEquals("Test Inner", innerUser.getName());
        assertEquals("norollbackinner@test.com", innerUser.getEmail());

        assertEquals("Test Outer", user.getName());
        assertEquals("norollbackouter@test.com", user.getEmail());
        BiUser dbuser = dao.fetchOneById(user.getId());
        assertEquals(user.getId(), dbuser.getId());
        assertEquals("Test Outer", dbuser.getName());
        assertEquals("norollbackouter@test.com", dbuser.getEmail());

        List<BiUser> users = dao.fetchByEmail("norollbackinner@test.com");
        BiUser inner = users.get(0);

        dao.deleteById(inner.getId());
        dao.deleteById(dbuser.getId());
    }

    @Test
    void nestedRollback() {
        DataAccessException e = assertThrows(DataAccessException.class, () -> {

            BiUser user = dsl.transactionResult(configuration -> {

                BiUser innerUser = DSL.using(configuration).transactionResult(configuration2 -> {
                    BiUser jooqUser = new BiUser();
                    jooqUser.setName("Test Inner");
                    jooqUser.setEmail("norollbackinner@test.com");
                    dao.insert(jooqUser);

                    throw new DoesNotExistException("test");
                });

                BiUser jooqUser = new BiUser();
                jooqUser.setName("Test Outer");
                jooqUser.setEmail("norollbackouter@test.com");
                dao.insert(jooqUser);
                return jooqUser;
            });
            }, "Rollback caused");

        List<BiUser> dbusers = dao.fetchByEmail("norollbackinner@test.com", "norollbackouter@test.com");
        assertEquals(0, dbusers.size());
        assertTrue(e.getCause() instanceof DoesNotExistException);
        assertEquals("test", e.getCause().getMessage());
    }
}
