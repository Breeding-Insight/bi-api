package org.breedinginsight.api.model.v1.validators;

import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.jooq.tools.StringUtils;

import javax.inject.Singleton;

@Factory
public class ValidatorFactory {
    @Singleton
    ConstraintValidator<AlwaysInvalid, CharSequence> emailStrictValidator() {
        return (value, annotationMetadata, context) ->
                false;
    }

    @Singleton
    ConstraintValidator<ProgramUserValid, ProgramUserRequest> programUserValidator() {
        return (value, annotationMetadata, context) ->
                // TODO: check e-mail, make sure same as other validations
                (value.getId() != null || (value.getName() != null && value.getEmail() != null)) && value.getRoleIds() != null;
    }
}
