package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.validate.field;

import io.micronaut.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.model.Trait;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.TIMESTAMP_PREFIX;
import static org.breedinginsight.dao.db.enums.DataType.NOMINAL;

@Slf4j
@Singleton
public class NominalValidator implements ObservationValidator {
    @Inject
    ObservationService observationService;

    public NominalValidator(ObservationService observationService) {
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

        // Skip if this is not an ordinal trait
        if (!NOMINAL.equals(variable.getScale().getDataType())) {
            return Optional.empty();
        }

        // Validate categories
        if (!observationService.validCategory(variable.getScale().getCategories(), value)) {
            return Optional.of(new ValidationError(fieldName, "Undefined nominal category detected", HttpStatus.UNPROCESSABLE_ENTITY));
        }

        return Optional.empty();
    }
}
