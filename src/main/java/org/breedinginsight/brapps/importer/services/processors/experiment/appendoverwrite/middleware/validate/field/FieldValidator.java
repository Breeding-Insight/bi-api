package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.validate.field;

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
