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

package org.breedinginsight.brapps.importer.services.processors.experiment.validator.field;

import io.micronaut.context.annotation.Primary;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.model.Trait;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * This class represents a FieldValidator that implements ObservationValidator interface to validate fields.
 * FieldValidator is a Primary and Singleton bean in the application.
 */
@Primary
@Singleton
public class FieldValidator implements ObservationValidator {

    /**
     * List of ObservationValidator instances to perform validation on fields.
     */
    private final List<ObservationValidator> validators;

    /**
     * Constructor for FieldValidator which accepts a list of ObservationValidator instances.
     * @param validators List of ObservationValidator instances
     */
    public FieldValidator(List<ObservationValidator> validators) {
        this.validators = validators;
    }

    /**
     * Validates a field by applying validation from multiple validators in the list.
     * Returns the first validation error encountered, if any.
     * @param fieldName The name of the field being validated
     * @param value The value of the field being validated
     * @param variable The trait variable associated with the field
     * @return Optional&lt;ValidationError&gt; Optional containing the first validation error encountered, if any
     */
    @Override
    public Optional<ValidationError> validateField(String fieldName, String value, Trait variable) {
        return validators.stream()
                .map(validator->validator.validateField(fieldName, value, variable))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
