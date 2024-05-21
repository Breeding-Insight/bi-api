package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.validate.field;

import io.micronaut.core.order.Ordered;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.model.Trait;

import java.util.Optional;

@FunctionalInterface
public interface ObservationValidator extends Ordered {
    Optional<ValidationError> validateField(String fieldName, String value, Trait variable);
}
