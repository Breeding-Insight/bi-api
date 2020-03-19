package org.breedinginsight.daos;

import io.micronaut.test.annotation.MicronautTest;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.model.User;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.inject.Inject;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * This is testing Jooq code, not ours, tests are here to demonstrate usage and api works as expected
 */

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DSLTransactionResultIntegrationTest {

    @Inject
    DSLContext dsl;

    @Inject
    UserDAO dao;
    @Inject
    UserService userService;

    User actingUser;

    @BeforeAll
    void setup() throws Exception {
        actingUser = userService.getByOrcid(TestTokenValidator.TEST_USER_ORCID);
    }

    @Test
    void noRollback() {
        BiUserEntity user = dsl.transactionResult(configuration -> {
            BiUserEntity jooqUser = new BiUserEntity();
            jooqUser.setName("Test User");
            jooqUser.setEmail("norollback@test.com");
            jooqUser.setCreatedBy(actingUser.getId());
            jooqUser.setUpdatedBy(actingUser.getId());
            dao.insert(jooqUser);
            return jooqUser;
        });
        assertEquals("Test User", user.getName());
        assertEquals("norollback@test.com", user.getEmail());
        BiUserEntity dbuser = dao.fetchOneById(user.getId());
        assertEquals(user.getId(), dbuser.getId());
        assertEquals("Test User", dbuser.getName());
        assertEquals("norollback@test.com", dbuser.getEmail());
        dao.deleteById(dbuser.getId());
    }

    @Test
    void rollback() {
        DataAccessException e = assertThrows(DataAccessException.class, () -> {
            BiUserEntity user = dsl.transactionResult(configuration -> {
                BiUserEntity jooqUser = new BiUserEntity();
            jooqUser.setName("Test User");
            jooqUser.setEmail("rollback@test.com");
            jooqUser.setCreatedBy(actingUser.getId());
            jooqUser.setUpdatedBy(actingUser.getId());
            dao.insert(jooqUser);
            throw new AlreadyExistsException("test");
        });
        }, "Rollback caused");

        List<BiUserEntity> dbusers = dao.fetchByEmail("rollback@test.com");
        assertEquals(0, dbusers.size());
        assertTrue(e.getCause() instanceof AlreadyExistsException);
        assertEquals("test", e.getCause().getMessage());
    }

    @Test
    void nestedNoRollback() {

        BiUserEntity user = dsl.transactionResult(configuration -> {

            BiUserEntity innerUser = DSL.using(configuration).transactionResult(configuration2 -> {
                BiUserEntity jooqUser = new BiUserEntity();
                jooqUser.setName("Test Inner");
                jooqUser.setEmail("norollbackinner@test.com");
                jooqUser.setCreatedBy(actingUser.getId());
                jooqUser.setUpdatedBy(actingUser.getId());
                dao.insert(jooqUser);
                return jooqUser;
            });

            BiUserEntity jooqUser = new BiUserEntity();
            jooqUser.setName("Test Outer");
            jooqUser.setEmail("norollbackouter@test.com");
            jooqUser.setCreatedBy(actingUser.getId());
            jooqUser.setUpdatedBy(actingUser.getId());
            dao.insert(jooqUser);
            return jooqUser;
        });

        BiUserEntity innerUser = dao.fetchByEmail("norollbackinner@test.com").get(0);
        assertEquals("Test Inner", innerUser.getName());
        assertEquals("norollbackinner@test.com", innerUser.getEmail());

        assertEquals("Test Outer", user.getName());
        assertEquals("norollbackouter@test.com", user.getEmail());
        BiUserEntity dbuser = dao.fetchOneById(user.getId());
        assertEquals(user.getId(), dbuser.getId());
        assertEquals("Test Outer", dbuser.getName());
        assertEquals("norollbackouter@test.com", dbuser.getEmail());

        List<BiUserEntity> users = dao.fetchByEmail("norollbackinner@test.com");
        BiUserEntity inner = users.get(0);

        dao.deleteById(inner.getId());
        dao.deleteById(dbuser.getId());
    }

    @Test
    void nestedRollback() {
        DataAccessException e = assertThrows(DataAccessException.class, () -> {

            BiUserEntity user = dsl.transactionResult(configuration -> {

                BiUserEntity innerUser = DSL.using(configuration).transactionResult(configuration2 -> {
                    BiUserEntity jooqUser = new BiUserEntity();
                    jooqUser.setName("Test Inner");
                    jooqUser.setEmail("norollbackinner@test.com");
                    jooqUser.setCreatedBy(actingUser.getId());
                    jooqUser.setUpdatedBy(actingUser.getId());
                    dao.insert(jooqUser);

                    throw new DoesNotExistException("test");
                });

                BiUserEntity jooqUser = new BiUserEntity();
                jooqUser.setName("Test Outer");
                jooqUser.setEmail("norollbackouter@test.com");
                dao.insert(jooqUser);
                return jooqUser;
            });
            }, "Rollback caused");

        List<BiUserEntity> dbusers = dao.fetchByEmail("norollbackinner@test.com", "norollbackouter@test.com");
        assertEquals(0, dbusers.size());
        assertTrue(e.getCause() instanceof DoesNotExistException);
        assertEquals("test", e.getCause().getMessage());
    }
}
