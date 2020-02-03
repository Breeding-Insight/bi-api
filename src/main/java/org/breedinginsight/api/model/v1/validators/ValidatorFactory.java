package org.breedinginsight.api.model.v1.validators;

import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;

import javax.inject.Singleton;

@Factory
public class ValidatorFactory {
    @Singleton
    ConstraintValidator<AlwaysInvalid, CharSequence> emailStrictValidator() {
        return (value, annotationMetadata, context) ->
                false;
    }
}
