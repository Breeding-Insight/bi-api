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
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.validator.dynamicColumns.observationUnitID.ObservationUnitIDValidator;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.EntityNotFoundException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPIReadFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowReadInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareException;
import org.breedinginsight.services.exceptions.BadRequestException;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Prototype
public class AppendOverwriteIDValidation extends AppendOverwriteMiddleware {
    WorkflowReadInitialization<BrAPIObservationUnit> brAPIObservationUnitReadWorkflowInitialization;
    BrAPIReadFactory brAPIReadFactory;
    ObservationUnitIDValidator ouIdValidator;

    @Inject
    public AppendOverwriteIDValidation(BrAPIReadFactory brAPIReadFactory, ObservationUnitIDValidator ouIdValidator) {
        this.brAPIReadFactory = brAPIReadFactory;
        this.ouIdValidator = ouIdValidator;
    }

    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        brAPIObservationUnitReadWorkflowInitialization = brAPIReadFactory.observationUnitWorkflowReadInitializationBean(context);

        // Initialize the tabular error collection
        Optional.ofNullable(context.getAppendOverwriteWorkflowContext().getValidationErrors()).orElseGet(() -> {
            context.getAppendOverwriteWorkflowContext().setValidationErrors(new ValidationErrors());
            return new ValidationErrors();
        });
        ValidationErrors validationErrors = context.getAppendOverwriteWorkflowContext().getValidationErrors();

        try {
            ouIdValidator.validateDynamicColumns(context);
            Set<String> uniqueOUIds = ExperimentUtilities.collateUniqueOUIds(context);
            context.getAppendOverwriteWorkflowContext().setReferenceOUIds(uniqueOUIds);

            // Check for tabular errors collected during validation
            if (validationErrors.hasErrors()) {
                throw new ValidatorException(validationErrors);
            }

            // Fetch the obs units from the BrAPi service
            brAPIObservationUnitReadWorkflowInitialization.execute();

            // Validate retrieved observation units
            ouIdValidator.validateDynamicColumns(context);

            return processNext(context);
        } catch (EntityNotFoundException e) {
            /**
             * Return an error response with a list of rows where the unique OU id was not found in the BrAPI service in
             * addition to rows where there are missing or duplicate OU ids
             */
            ExperimentUtilities.addValidationErrorsForObsUnitsNotFound(e, context);
            context.getAppendOverwriteWorkflowContext().setProcessError(new MiddlewareException(new ValidatorException(validationErrors)));
            return this.compensate(context);
        } catch (BadRequestException | ApiException | ValidatorException e) {
            /**
             * If OUs were fetched for all unique reference ids but some of the reference ids failed validation,
             * return an error response and a list of rows with duplicate or missing ids
             *
             * Return an error response if there was a problem connecting to the BrAPI service
             */
            context.getAppendOverwriteWorkflowContext().setProcessError(new MiddlewareException(e));
            return this.compensate(context);
        }

    }
}
