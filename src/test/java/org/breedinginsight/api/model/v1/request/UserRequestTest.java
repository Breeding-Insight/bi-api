/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.api.model.v1.request;

import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.validation.validator.Validator;
import org.breedinginsight.DatabaseTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class UserRequestTest extends DatabaseTest {
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
