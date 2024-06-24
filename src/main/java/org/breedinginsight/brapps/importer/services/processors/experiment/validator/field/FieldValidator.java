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

@Primary
@Singleton
public class FieldValidator implements ObservationValidator {
    private final List<ObservationValidator> validators;

    public FieldValidator(List<ObservationValidator> validators) {
        this.validators = validators;
    }

    @Override
    public Optional<ValidationError> validateField(String fieldName, String value, Trait variable) {
        return validators.stream()
                .map(validator->validator.validateField(fieldName, value, variable))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }
}
