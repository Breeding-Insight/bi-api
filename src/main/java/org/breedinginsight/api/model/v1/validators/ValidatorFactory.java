/*
 * See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
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
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import org.breedinginsight.api.model.v1.request.UserIdRequest;

import javax.inject.Singleton;

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
}
