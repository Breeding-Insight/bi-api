package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import lombok.SneakyThrows;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        HttpResponse createProgramHttpResponse = programController.createProgram(principal, new ProgramRequest());
        assertEquals(HttpStatus.UNAUTHORIZED, createProgramHttpResponse.getStatus());
        HttpResponse updateProgramHttpResponse = programController.updateProgram(principal, UUID.randomUUID(), new ProgramRequest());
        assertEquals(HttpStatus.UNAUTHORIZED, updateProgramHttpResponse.getStatus());
        HttpResponse archiveProgramHttpResponse = programController.archiveProgram(principal, UUID.randomUUID());
        assertEquals(HttpStatus.UNAUTHORIZED, archiveProgramHttpResponse.getStatus());
        HttpResponse addProgramUserHttpResponse = programController.addProgramUser(principal, UUID.randomUUID(), new ProgramUserRequest());
        assertEquals(HttpStatus.UNAUTHORIZED, addProgramUserHttpResponse.getStatus());
        HttpResponse updateProgramUserHttpResponse = programController.updateProgramUser(principal, UUID.randomUUID(), UUID.randomUUID(),new ProgramUserRequest());
        assertEquals(HttpStatus.UNAUTHORIZED, updateProgramUserHttpResponse.getStatus());

        // User endpoints
        //HttpResponse createUserHttpResponse = userController.createUser(principal, new UserRequest());
        //assertEquals(HttpStatus.UNAUTHORIZED, createUserHttpResponse.getStatus());
        HttpResponse updateUserHttpResponse = userController.updateUser(principal, UUID.randomUUID(), new UserRequest());
        assertEquals(HttpStatus.UNAUTHORIZED, updateUserHttpResponse.getStatus());
    }

}
