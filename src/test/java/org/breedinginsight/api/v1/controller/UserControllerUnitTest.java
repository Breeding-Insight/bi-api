package org.breedinginsight.api;

import io.micronaut.http.HttpStatus;
import org.breedinginsight.api.bi.model.v1.request.UserRequest;
import org.breedinginsight.api.bi.v1.controller.UserController;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import io.micronaut.http.HttpResponse;
import org.mockito.MockitoAnnotations;

import java.security.Principal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/*
 * Unit tests of UserController endpoints using Mockito mocks
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UserControllerUnitTest {

    @Mock
    DSLContext dsl;

    @InjectMocks
    UserController userController;

    @BeforeEach
    void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    Principal principal = new Principal() {
        @Override
        public String getName() {
            return "test";
        }
    };

    @Test
    public void getUsersSingleDataAccessException() {
        // select doesn't throw, fetchOne does but just using select for easier mocking
        when(dsl.select()).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.users(principal);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getUserInfoDataAccessException() {
        when(dsl.fetchOne((Table<Record>) any(), (Condition) any())).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.userinfo(principal);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getUsersDataAccessException() {
        when(dsl.fetchOne((Table<Record>) any(), (Condition) any())).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.users(principal, UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void postUsersDataAccessException() {
        // selectCount doesn't throw, fetchOne does but just using select for easier mocking
        when(dsl.selectCount()).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.createUser(principal, new UserRequest("Test User", "test@test.com"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void putUsersDataAccessException() {
        when(dsl.fetchOne((Table<Record>) any(), (Condition) any())).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.updateUser(principal, UUID.randomUUID(), new UserRequest());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void deleteUserDataAccessException() {
        when(dsl.fetchOne((Table<Record>) any(), (Condition) any())).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.updateUser(principal, UUID.randomUUID(), new UserRequest());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

}
