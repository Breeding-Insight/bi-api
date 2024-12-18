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
import static org.breedinginsight.dao.db.enums.DataType.DATE;

/**
 * This class represents a DateValidator that implements the ObservationValidator interface.
 * It is responsible for validating date fields in observations based on specific criteria.
 */
@Slf4j
@Singleton
public class DateValidator implements ObservationValidator {

    @Inject
    ObservationService observationService;

    private final String dateMessage = "Incorrect date format detected. Expected YYYY-MM-DD";
    private final String dateTimeMessage = "Incorrect datetime format detected. Expected YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+hh:mm";

    /**
     * Constructor for the DateValidator class that initializes the ObservationService instance.
     * @param observationService An instance of ObservationService used for validation.
     */
    public DateValidator(ObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * Validates a specific field in an observation based on the value and traits provided.
     *
     * @param fieldName The name of the field to validate.
     * @param value The value of the field to be validated.
     * @param variable The trait variable associated with the field.
     * @return An Optional containing a ValidationError if validation fails, or Optional.empty() if validation passes.
     */
    @Override
    public Optional<ValidationError> validateField(String fieldName, String value, Trait variable) {
        // Check if the observation is blank
        if (observationService.isBlankObservation(value)) {
            log.debug(String.format("Skipping validation of observation because there is no value.\n\tVariable: %s", fieldName));
            return Optional.empty();
        }

        // Check if the observation is NA
        if (observationService.isNAObservation(value)) {
            log.debug(String.format("Skipping validation of observation because it is NA.\n\tVariable: %s", fieldName));
            return Optional.empty();
        }

        // Check if the field is a timestamp
        if (fieldName.startsWith(TIMESTAMP_PREFIX)) {
            if (!observationService.validDateValue(value) && !observationService.validDateTimeValue(value)) {
                return Optional.of(new ValidationError(fieldName, dateTimeMessage, HttpStatus.UNPROCESSABLE_ENTITY));
            }

        } else {
            // Skip if there is no trait data
            if (variable == null || variable.getScale() == null || variable.getScale().getDataType() == null) {
                return Optional.empty();
            }

            // Skip if this is not a date trait
            if (!DATE.equals(variable.getScale().getDataType())) {
                return Optional.empty();
            }

            // Validate date
            if (!observationService.validDateValue(value)) {
                return Optional.of(new ValidationError(fieldName, dateMessage, HttpStatus.UNPROCESSABLE_ENTITY));
            }
        }

        return Optional.empty();
    }
}
