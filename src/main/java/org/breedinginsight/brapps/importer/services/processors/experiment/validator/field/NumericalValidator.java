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
import java.math.BigDecimal;
import java.util.Optional;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.TIMESTAMP_PREFIX;
import static org.breedinginsight.dao.db.enums.DataType.NUMERICAL;

/**
 * NumericalValidator class is responsible for validating numerical observations against specified constraints.
 * This class implements the ObservationValidator interface.
 *
 * The class provides a validateField method which takes the field name, value, and trait as inputs and
 * returns an Optional containing a ValidationError if any validation error is encountered, or Optional.empty() if
 * the validation passes successfully.
 */
@Slf4j
@Singleton
public class NumericalValidator implements ObservationValidator {

    @Inject
    ObservationService observationService;

    /**
     * Constructor for NumericalValidator class.
     * @param observationService An instance of ObservationService to perform observation-related operations.
     */
    public NumericalValidator(ObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * Validates a numerical observation against specified constraints.
     * Returns an Optional containing a ValidationError if validation fails, or Optional.empty() if successful.
     * @param fieldName The name of the observation field to be validated.
     * @param value The value of the observation to be validated.
     * @param variable The trait associated with the observation.
     * @return Optional containing a ValidationError if validation fails, or Optional.empty() if successful.
     */
    @Override
    public Optional<ValidationError> validateField(String fieldName, String value, Trait variable) {
        if (observationService.isBlankObservation(value)) {
            log.debug(String.format("Skipping validation of observation because there is no value.\n\tVariable: %s", fieldName));
            return Optional.empty();
        }

        if (observationService.isNAObservation(value)) {
            log.debug(String.format("Skipping validation of observation because it is NA.\n\tVariable: %s", fieldName));
            return Optional.empty();
        }

        // Skip validation if field is a timestamp
        if (fieldName.startsWith(TIMESTAMP_PREFIX)) {
            return Optional.empty();
        }

        // Skip validation if there is no trait data or if the trait is not numerical
        if (variable == null || variable.getScale() == null || variable.getScale().getDataType() == null ||
                !NUMERICAL.equals(variable.getScale().getDataType())) {
            return Optional.empty();
        }

        // Check if the value is a valid numeric value
        Optional<BigDecimal> number = observationService.validNumericValue(value);
        if (number.isEmpty()) {
            return Optional.of(new ValidationError(fieldName, "Non-numeric text in a numerical field", HttpStatus.UNPROCESSABLE_ENTITY));
        }

        // Perform range validation for numeric value
        Optional<ValidationError> validationError = number
                .flatMap(num -> {
                    if (observationService.validNumericRange(num, variable.getScale())) {
                        return Optional.empty(); // Return empty Optional if value is within numeric range
                    } else {
                        return Optional.of(new ValidationError(fieldName, "Value outside of min/max range detected", HttpStatus.UNPROCESSABLE_ENTITY));
                    }
                });

        return validationError;
    }
}