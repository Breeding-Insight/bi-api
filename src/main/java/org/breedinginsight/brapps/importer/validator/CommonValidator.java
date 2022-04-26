package org.breedinginsight.brapps.importer.validator;

import io.micronaut.http.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;

public class CommonValidator {

    private final static String requiredErr = "Required field, \"%s\" cannot be empty.";

    public static void required(String field, Integer rowIndex, String value, ValidationErrors errors) {
        if (StringUtils.isBlank(value)) {
            ValidationError validationError = new ValidationError();
            validationError.setField(field);
            validationError.setErrorMessage(String.format(requiredErr, field));
            validationError.setHttpStatus(HttpStatus.BAD_REQUEST);
            validationError.setHttpStatusCode(HttpStatus.BAD_REQUEST.getCode());
            errors.addRowError(rowIndex, validationError);
        }
    }

    public static void required(String field, String value, ValidationErrors errors) {
        if (StringUtils.isBlank(value)) {
            errors.addError(field, HttpStatus.BAD_REQUEST, String.format(requiredErr, field));
        }
    }
}
