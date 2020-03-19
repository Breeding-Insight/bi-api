package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpStatus;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/*
 * Unit tests of UserController endpoints using Mockito mocks
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UserControllerUnitTest {

    @Mock
    UserService userService;

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
    public void getUsersSingleDataAccessException() throws DoesNotExistException {
        // select doesn't throw, fetchOne does but just using select for easier mocking
        when(userService.getById(any(UUID.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.users(UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getUserInfoDataAccessException() throws DoesNotExistException {
        when(userService.getByOrcid(anyString())).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.userinfo(principal);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getUsersDataAccessException() {
        when(userService.getAll()).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.users();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void postUsersDataAccessException() throws AlreadyExistsException, MissingRequiredInfoException {
        // selectCount doesn't throw, fetchOne does but just using select for easier mocking
        when(userService.create(any(), any())).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.createUser(principal, new UserRequest("Test User", "test@test.com"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void putUsersDataAccessException() throws AlreadyExistsException, DoesNotExistException {
        when(userService.update(any(), any(), any())).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.updateUser(principal, UUID.randomUUID(), new UserRequest());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void deleteUserDataAccessException() throws DoesNotExistException {
        doThrow(new DataAccessException("TEST")).when(userService).delete(any(UUID.class));
        //when(userService.delete(any(UUID.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = userController.deleteUser(UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

}
