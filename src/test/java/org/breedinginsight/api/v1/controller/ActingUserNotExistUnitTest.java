package org.breedinginsight.api.v1.controller;

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.SneakyThrows;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.api.v1.controller.ProgramController;
import org.breedinginsight.api.v1.controller.TestTokenValidator;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.User;
import org.breedinginsight.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ActingUserNotExistUnitTest {

    @Mock
    UserService userService;

    @InjectMocks
    ProgramController programController;
    @InjectMocks
    UserController userController;

    Principal principal = new Principal() {
        @Override
        public String getName() {
            return TestTokenValidator.TEST_USER_ORCID;
        }
    };

    @BeforeEach
    void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @SneakyThrows
    void controllerActiveUserNotExist(){
        when(userService.getByOrcid(any(String.class))).thenReturn(Optional.empty());

        // Program endpoints
        assertThrows(InternalServerException.class, () ->
                programController.createProgram(principal, new ProgramRequest()));
        assertThrows(InternalServerException.class, () ->
                programController.updateProgram(principal, UUID.randomUUID(), new ProgramRequest()));
        assertThrows(InternalServerException.class, () ->
                programController.archiveProgram(principal, UUID.randomUUID()));
        assertThrows(InternalServerException.class, () ->
                programController.addProgramUser(principal, UUID.randomUUID(), new ProgramUserRequest()));
        assertThrows(InternalServerException.class, () ->
                programController.updateProgramUser(principal, UUID.randomUUID(), UUID.randomUUID(),new ProgramUserRequest()));

        // User endpoints
        assertThrows(InternalServerException.class, () ->
                userController.createUser(principal, new UserRequest()));
        assertThrows(InternalServerException.class, () ->
                userController.updateUser(principal, UUID.randomUUID(), new UserRequest()));
    }

}
