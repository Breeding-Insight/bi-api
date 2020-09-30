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

package org.breedinginsight.api.model.v1.validators;

import io.micronaut.context.annotation.Factory;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import org.breedinginsight.api.model.v1.request.UserIdRequest;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.utilities.response.mappers.AbstractQueryMapper;

import javax.inject.Singleton;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Collectors;

@Factory
public class ValidatorFactory {
    @Singleton
    ConstraintValidator<AlwaysInvalid, CharSequence> emailStrictValidator() {
        return (value, annotationMetadata, context) ->
                false;
    }

    @Singleton
    ConstraintValidator<UserIdValid, UserIdRequest> userIdValidator() {
        return (value, annotationMetadata, context) ->
                // TODO: check e-mail, make sure same as other validations
                value != null && (value.getId() != null || (value.getName() != null && value.getEmail() != null));
    }

    @Singleton
    ConstraintValidator<SearchValid, SearchRequest> searchBodyValidator() {
        return (value, annotationMetadata, context) -> {
            Class mapperClass = annotationMetadata.getRequiredValue("using", Class.class);
            List<FilterRequest> filterFields = value.getFilter();
            if (filterFields == null) return true;
            List<String> fields = filterFields.stream().map(FilterRequest::getField).collect(Collectors.toList());
            return checkMappedFields(fields, mapperClass);
        };
    }

    @Singleton
    ConstraintValidator<QueryValid, QueryParams> mappedFieldValidator() {

        return (value, annotationMetadata, context) -> {
            Class mapperClass = annotationMetadata.getRequiredValue("using", Class.class);
            if (value == null || value.getSortField() == null) {
                return true;
            } else {
                return checkMappedFields(List.of(value.getSortField()), mapperClass);
            }
        };
    }

    private static Boolean checkMappedFields(List<String> filterFieldNames, Class mapperClass) {

        AbstractQueryMapper mapper;
        try {
            Constructor mapperClassConstructor = mapperClass.getConstructor();
            mapper = (AbstractQueryMapper) mapperClassConstructor.newInstance();
        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw new HttpServerException("Could not instantiate mapper class");
        }

        List<String> nonExistFields = filterFieldNames.stream()
                .filter(fieldName -> !mapper.exists(fieldName))
                .collect(Collectors.toList());

        return nonExistFields.size() == 0;
    }


}
