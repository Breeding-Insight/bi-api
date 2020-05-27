package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpStatus;
import org.breedinginsight.api.model.v1.request.AccessibilityRequest;
import org.breedinginsight.services.AccessibilityService;
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
 * Unit tests of AccessibilityController endpoints using Mockito mocks
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AccessibilityControllerUnitTest {

    @Mock
    AccessibilityService accessibilityService;

    @InjectMocks
    AccessibilityController accessibilityController;

    @BeforeEach
    void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getAccessibilitiesDataAccessException() {
        when(accessibilityService.getAll()).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = accessibilityController.getAccessibilities();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    @Test
    public void getAccessibilitySingleDataAccessException() throws DoesNotExistException {
        // select doesn't throw, fetchOne does but just using select for easier mocking
        when(accessibilityService.getById(any(UUID.class))).thenThrow(new DataAccessException("TEST"));
        HttpResponse response = accessibilityController.getAccessibility(UUID.randomUUID());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

}
