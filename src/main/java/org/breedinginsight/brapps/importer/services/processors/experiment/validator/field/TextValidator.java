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

import io.micronaut.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.model.Trait;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.TIMESTAMP_PREFIX;
import static org.breedinginsight.dao.db.enums.DataType.TEXT;

//so much todotodotod


/**
 * This class represents a TextValidator which implements the ObservationValidator interface.
 * It is responsible for validating text fields within observations.
 */
@Slf4j
@Singleton
public class TextValidator implements ObservationValidator {

    @Inject
    ObservationService observationService;

    /**
     * Constructor for TextValidator class that takes an ObservationService as a parameter.
     * @param observationService the ObservationService used for validation
     */
    public TextValidator(ObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * Validates a field within an observation for text data.
     *
     * @param fieldName the name of the field being validated
     * @param value the value of the field being validated
     * @param variable the Trait variable associated with the field
     * @return an Optional containing a ValidationError if validation fails, otherwise an empty Optional
     */
    @Override
    public Optional<ValidationError> validateField(String fieldName, String value, Trait variable) {
        // Skip validation if observation is blank
        if (observationService.isBlankObservation(value)) {
            log.debug(String.format("Skipping validation of observation because there is no value.\n\tvariable: %s", fieldName));
            return Optional.empty();
        }

        // Skip validation if observation is NA
        if (observationService.isNAObservation(value)) {
            log.debug(String.format("Skipping validation of observation because it is NA.\n\tvariable: %s", fieldName));
            return Optional.empty();
        }

        // Skip if field is a timestamp
        if (fieldName.startsWith(TIMESTAMP_PREFIX)) {
            return Optional.empty();
        }

        // Skip if there is no trait data
        if (variable == null || variable.getScale() == null || variable.getScale().getDataType() == null) {
            return Optional.empty();
        }

        // Skip if this is not a text trait
        if (!TEXT.equals(variable.getScale().getDataType())) {
            return Optional.empty();
        }

        // Validate text
        if (!observationService.validText(value)) {
            return Optional.of(new ValidationError(fieldName, "'Null' is not a valid value", HttpStatus.UNPROCESSABLE_ENTITY));
        }

        return Optional.empty();
    }
}