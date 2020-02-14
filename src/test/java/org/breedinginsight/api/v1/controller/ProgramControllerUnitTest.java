package org.breedinginsight.api.v1.controller;

/*
 * Unit tests of UserController endpoints using Mockito mocks
 */

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.Principal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

public class ProgramControllerUnitTest {

    @Mock
    ProgramService programService;

    @InjectMocks
    ProgramController programController;

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
    public void getProgramsSingleDataAccessException() throws DoesNotExistException {
        when(programService.getById(any(UUID.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.getProgram(UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getProgramsAllDataAccessException() {
        when(programService.getAll()).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.getPrograms();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void postProgramsDataAccessException() throws AlreadyExistsException {
        when(programService.create(any(ProgramRequest.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.createProgram(new ProgramRequest());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void putProgramsDataAccessException() throws DoesNotExistException {
        when(programService.update(any(UUID.class), any(ProgramRequest.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.updateProgram(UUID.randomUUID(), new ProgramRequest());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void deleteProgramsDataAccessException() throws DoesNotExistException {
        doThrow(new DataAccessException("TEST")).when(programService).archive(any(UUID.class));
        HttpResponse response = programController.archiveProgram(UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getProgramUsersAllDataAccessException() throws DoesNotExistException {
        when(programService.getProgramUsers(any(UUID.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.getProgramUsers(UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getProgramUsersSingleDataAccessException() throws DoesNotExistException {
        when(programService.getProgramUserbyId(any(UUID.class), any(UUID.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.getProgramUser(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void postProgramUsersDataAccessException() throws DoesNotExistException, AlreadyExistsException {
        when(programService.addProgramUser(any(UUID.class), any(ProgramUserRequest.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.addProgramUser(UUID.randomUUID(), new ProgramUserRequest());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void deleteProgramUsersDataAccessException() throws DoesNotExistException {
        doThrow(new DataAccessException("TEST")).when(programService).removeProgramUser(any(UUID.class), any(UUID.class));
        HttpResponse response = programController.removeProgramUser(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getProgramLocationsAllDataAccessException() throws DoesNotExistException {
        when(programService.getProgramLocations(any(UUID.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.getProgramLocations(UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getProgramLocationsSingleDataAccessException() throws DoesNotExistException {
        when(programService.getProgramLocation(any(UUID.class), any(UUID.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.getProgramLocations(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void postProgramLocationsDataAccessException() throws DoesNotExistException, AlreadyExistsException {
        when(programService.addProgramLocation(any(UUID.class), any(ProgramLocationRequest.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = programController.addProgramLocation(UUID.randomUUID(), new ProgramLocationRequest());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void deleteProgramLocationsDataAccessException() throws DoesNotExistException {
        doThrow(new DataAccessException("TEST")).when(programService).removeProgramLocation(any(UUID.class), any(UUID.class));
        HttpResponse response = programController.removeProgramLocation(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

}
