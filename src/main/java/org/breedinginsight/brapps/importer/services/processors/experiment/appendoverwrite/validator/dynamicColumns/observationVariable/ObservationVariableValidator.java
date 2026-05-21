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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.validator.dynamicColumns.observationVariable;

import io.micronaut.context.annotation.Primary;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.services.exceptions.BadRequestException;

import javax.inject.Singleton;
import java.util.List;

@Primary
@Singleton
public class ObservationVariableValidator implements DynamicObsVarValidator {
    private final List<DynamicObsVarValidator> validators;

    public ObservationVariableValidator(List<DynamicObsVarValidator> validators) {
        this.validators = validators;
    }

    @Override
    public void validateDynamicColumns(AppendOverwriteMiddlewareContext ctx)
            throws BadRequestException, ApiException {
        for (DynamicObsVarValidator validator : validators) {
            validator.validateDynamicColumns(ctx);
        }
    }
}
