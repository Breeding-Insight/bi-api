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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.BrAPIState;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPICreationFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPIUpdateFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPIUpdateFactory.WorkflowUpdate;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.Optional;

@Slf4j
@Prototype
public class BrAPIObservationCommit extends AppendOverwriteMiddleware {
    private final BrAPICreationFactory brAPICreationFactory;
    private final BrAPIUpdateFactory brAPIUpdateFactory;
    private Optional<WorkflowCreation<BrAPIObservation>.BrAPICreationState<BrAPIObservation>> createdBrAPIObservations;
    private Optional<BrAPIUpdateFactory.WorkflowUpdate<BrAPIObservation>.BrAPIUpdateState<BrAPIObservation>> priorBrAPIObservations;

    @Inject
    public BrAPIObservationCommit(BrAPICreationFactory brAPICreationFactory, BrAPIUpdateFactory brAPIUpdateFactory) {
        this.brAPICreationFactory = brAPICreationFactory;
        this.brAPIUpdateFactory = brAPIUpdateFactory;
    }
    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        try {
            WorkflowCreation<BrAPIObservation> brAPIObservationCreation = brAPICreationFactory.observationWorkflowCreationBean(context);

            log.info("creating new observations in the BrAPI service");
            createdBrAPIObservations = brAPIObservationCreation.execute().map(s -> (WorkflowCreation<BrAPIObservation>.BrAPICreationState<BrAPIObservation>) s);
            WorkflowUpdate<BrAPIObservation> brAPIObservationUpdate = brAPIUpdateFactory.observationWorkflowUpdateBean(context);
            priorBrAPIObservations = brAPIObservationUpdate.getBrAPIState();

            log.info("updating existing observations in the BrAPI service");
            brAPIObservationUpdate.execute().map(s -> (WorkflowUpdate<BrAPIObservation>.BrAPIUpdateState<BrAPIObservation>) s);

        } catch (ApiException | MissingRequiredInfoException | UnprocessableEntityException | DoesNotExistException e) {
            context.getAppendOverwriteWorkflowContext().setProcessError(new MiddlewareException(e));
            return this.compensate(context);
        }

        return processNext(context);
    }

    @Override
    public AppendOverwriteMiddlewareContext compensate(AppendOverwriteMiddlewareContext context) {
        // Tag an error if it occurred in this local transaction
        context.getAppendOverwriteWorkflowContext().getProcessError().tag(this.getClass().getName());


            // Delete any created observations from the BrAPI service
            createdBrAPIObservations.ifPresent(WorkflowCreation.BrAPICreationState::undo);

            // Revert any changes made to observations in the BrAPI service
            priorBrAPIObservations.ifPresent(state -> {
                try {
                    state.restore();
                } catch (ApiException e) {
                    log.error("Error trying to restore BrAPI variable state: " + Utilities.generateApiExceptionLogMessage(e), e);
                }
            });

        // Undo the prior local transaction
        return compensatePrior(context);
    }
}
