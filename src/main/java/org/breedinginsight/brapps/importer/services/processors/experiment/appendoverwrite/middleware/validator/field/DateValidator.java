package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.validator.field;

import io.micronaut.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.validator.field.ObservationValidator;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationService;
import org.breedinginsight.model.Trait;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.TIMESTAMP_PREFIX;
import static org.breedinginsight.dao.db.enums.DataType.DATE;

@Slf4j
@Singleton
public class DateValidator implements ObservationValidator {
    @Inject
    ObservationService observationService;
    private final String dateMessage = "Incorrect date format detected. Expected YYYY-MM-DD";
    private final String dateTimeMessage = "Incorrect datetime format detected. Expected YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+hh:mm";

    public DateValidator(ObservationService observationService) {
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

        // Is this a timestamp field?
        if (fieldName.startsWith(TIMESTAMP_PREFIX)) {
            if (!observationService.validDateValue(value) || !observationService.validDateTimeValue(value)) {
                return Optional.of(new ValidationError(fieldName, dateTimeMessage, HttpStatus.UNPROCESSABLE_ENTITY));
            }

        } else {

            // Skip if there is no trait data
            if (variable == null || variable.getScale() == null || variable.getScale().getDataType() == null) {
                return Optional.empty();
            }

            // Skip if this is not a date trait
            if (!DATE.equals(variable.getScale().getDataType())) {
                return Optional.empty();
            }

            // Validate date
            if (!observationService.validDateValue(value)) {
                return Optional.of(new ValidationError(fieldName, dateMessage, HttpStatus.UNPROCESSABLE_ENTITY));
            }
        }

        return Optional.empty();
    }
}
