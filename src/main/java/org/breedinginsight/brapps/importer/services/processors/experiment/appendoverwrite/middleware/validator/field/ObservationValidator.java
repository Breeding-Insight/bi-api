package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.validator.field;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.model.Trait;

import java.util.Optional;

@FunctionalInterface
public interface ObservationValidator extends Ordered {
    Optional<ValidationError> validateField(@NonNull String fieldName, @NonNull String value, Trait variable);
}
