package org.breedinginsight.api.model.v1.request;

import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.validation.validator.Validator;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class UserRequestTest {
    @Inject
    Validator validator;

    @Test
    void validRequest() {
        UserRequest request = new UserRequest();
        request.setEmail("test@test.com");
        request.setName("Test Tester");

        final Set<ConstraintViolation<UserRequest>> constraintViolations = validator.validate(request);

        assertEquals(0, constraintViolations.size());
    }

    @Test
    void invalidMissingAtAndDotEmail() {
        UserRequest request = new UserRequest();
        request.setEmail("test");
        request.setName("Test Tester");

        final Set<ConstraintViolation<UserRequest>> constraintViolations = validator.validate(request);

        assertEquals(1, constraintViolations.size());
    }

    @Test
    void validEmail() {
        UserRequest request = new UserRequest();
        request.setEmail("test.tester@test.com");
        request.setName("Test Tester");

        final Set<ConstraintViolation<UserRequest>> constraintViolations = validator.validate(request);

        assertEquals(0, constraintViolations.size());
    }

    // This is valid according to the RFC even though in practice would not be likely to be correct
    // Since emails will be validated via a registration email receipt, it should be ok
    @Test
    void validMissingDotEmail() {
        UserRequest request = new UserRequest();
        request.setEmail("test@test");
        request.setName("Test Tester");

        final Set<ConstraintViolation<UserRequest>> constraintViolations = validator.validate(request);

        assertEquals(0, constraintViolations.size());
    }

    @Test
    void invalidEndingDotEmail() {
        UserRequest request = new UserRequest();
        request.setEmail("test@test.");
        request.setName("Test Tester");

        final Set<ConstraintViolation<UserRequest>> constraintViolations = validator.validate(request);

        assertEquals(1, constraintViolations.size());
    }


    @Test
    void invalidBlankEmail() {
        UserRequest request = new UserRequest();
        request.setEmail("");
        request.setName("Test");

        final Set<ConstraintViolation<UserRequest>> constraintViolations = validator.validate(request);

        assertEquals(1, constraintViolations.size());
    }

    @Test
    void invalidNullEmail() {
        UserRequest request = new UserRequest();
        request.setName("Test");

        final Set<ConstraintViolation<UserRequest>> constraintViolations = validator.validate(request);

        assertEquals(1, constraintViolations.size());
    }

    @Test
    void invalidBlankName() {
        UserRequest request = new UserRequest();
        request.setEmail("test@test.com");
        request.setName("");

        final Set<ConstraintViolation<UserRequest>> constraintViolations = validator.validate(request);

        assertEquals(1, constraintViolations.size());
    }

    @Test
    void invalidNullName() {
        UserRequest request = new UserRequest();
        request.setEmail("test@test.com");

        final Set<ConstraintViolation<UserRequest>> constraintViolations = validator.validate(request);

        assertEquals(1, constraintViolations.size());
    }

}
