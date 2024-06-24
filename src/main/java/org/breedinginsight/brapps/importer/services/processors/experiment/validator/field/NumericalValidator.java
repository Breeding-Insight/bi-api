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

@Slf4j
@Singleton
public class NumericalValidator implements ObservationValidator {
    @Inject
    ObservationService observationService;

    public NumericalValidator(ObservationService observationService) {
        this.observationService = observationService;
    }
    @Override
    public Optional<ValidationError> validateField(String fieldName, String value, Trait variable) {
        if (observationService.isBlankObservation(value)) {
            log.debug(String.format("skipping validation of observation because there is no value.\n\tvariable: %s", fieldName));
            return Optional.empty();
        }

        if (observationService.isNAObservation(value)) {
            log.debug(String.format("skipping validation of observation because it is NA.\n\tvariable: %s", fieldName));
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

        // Skip if this is not a numerical trait
        if (!NUMERICAL.equals(variable.getScale().getDataType())) {
            return Optional.empty();
        }

        Optional<BigDecimal> number = observationService.validNumericValue(value);
        Optional<ValidationError> validationError = number
                .flatMap(num -> {
                    if (observationService.validNumericRange(num, variable.getScale())) {
                        return Optional.empty(); // Return empty Optional if value is in numeric range
                    } else {
                        return Optional.of(new ValidationError(fieldName, "Value outside of min/max range detected", HttpStatus.UNPROCESSABLE_ENTITY));
                    }
                });

        return validationError;

    }
}
