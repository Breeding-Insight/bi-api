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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPIReadFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowReadInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.validator.dynamicColumns.observationVariable.ObservationVariableValidator;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.EntityNotFoundException;
import org.breedinginsight.services.exceptions.BadRequestException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.api.model.v1.response.ValidationErrors;

@Slf4j
@Prototype
public class AppendOverwriteVariableValidation extends AppendOverwriteMiddleware {
    private final ObservationVariableValidator obsVarValidator;
    private final BrAPIReadFactory brAPIReadFactory;
    WorkflowReadInitialization<BrAPITrial> brAPITrialReadWorkflowInitialization;
    WorkflowReadInitialization<BrAPIListDetails> brAPIDatasetReadWorkflowInitialization;

    public AppendOverwriteVariableValidation(ObservationVariableValidator obsVarValidator,
                                             BrAPIReadFactory brAPIReadFactory) {
        this.obsVarValidator = obsVarValidator;
        this.brAPIReadFactory = brAPIReadFactory;
    }

    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        brAPITrialReadWorkflowInitialization = brAPIReadFactory.trialWorkflowReadInitializationBean(context);
        brAPIDatasetReadWorkflowInitialization = brAPIReadFactory.datasetWorkflowReadInitializationBean(context);
        ValidationErrors validationErrors = context.getAppendOverwriteWorkflowContext().getValidationErrors();

        try {
            // Validate any dynamic columns that are phenotype variables
            obsVarValidator.validateDynamicColumns(context);

            // Check for tabular errors collected during validation
            if (validationErrors.hasErrors()) {
                throw new ValidatorException(validationErrors);
            }

            // Fetch the observation variables owned by all datasets belonging to the experiment involved in the import
            brAPITrialReadWorkflowInitialization.execute();
            brAPIDatasetReadWorkflowInitialization.execute();

            // Validate again to check that none of the phenotypes in the import belong to other datasets
            obsVarValidator.validateDynamicColumns(context);

            return processNext(context);
        } catch ( EntityNotFoundException e) {
            // TODO: change method to handle errors with other entities besides obs units
            ExperimentUtilities.addValidationErrorsForObsUnitsNotFound(e, context);
            context.getAppendOverwriteWorkflowContext().setProcessError(new MiddlewareException(new ValidatorException(validationErrors)));
            return this.compensate(context);
        } catch (BadRequestException | ApiException | ValidatorException e) {
            context.getAppendOverwriteWorkflowContext().setProcessError(new MiddlewareException(e));
            return this.compensate(context);
        }
    }
}
