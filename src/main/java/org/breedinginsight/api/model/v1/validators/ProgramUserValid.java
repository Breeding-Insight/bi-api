package org.breedinginsight.api.model.v1.validators;

import org.breedinginsight.api.model.v1.request.ProgramUserRequest;

import javax.validation.Constraint;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Constraint(validatedBy = {})
public @interface ProgramUserValid {
    String message() default "invalid program user request body ({validatedValue})";

    /**
     * Defines several constraints on the same element.
     */
    @Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
    @Retention(RUNTIME)
    @Documented
    @interface List {
        ProgramUserValid[] value();
    }
}
