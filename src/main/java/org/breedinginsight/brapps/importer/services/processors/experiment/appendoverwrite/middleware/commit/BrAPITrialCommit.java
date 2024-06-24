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
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPICreationFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowCreation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPIUpdateFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowUpdate;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareException;

import javax.inject.Inject;
import java.util.Optional;

@Slf4j
@Prototype
public class BrAPITrialCommit extends AppendOverwriteMiddleware {
    private final BrAPICreationFactory brAPICreationFactory;
    private final BrAPIUpdateFactory brAPIUpdateFactory;
    private WorkflowCreation<BrAPITrial> brAPITrialCreation;
    private WorkflowUpdate<BrAPITrial> brAPITrialUpdate;
    private Optional<WorkflowCreation.BrAPICreationState> createdBrAPITrials;
    private Optional<WorkflowUpdate.BrAPIUpdateState> priorBrAPITrials;
    private Optional<WorkflowUpdate.BrAPIUpdateState> updatedTrials;

    @Inject
    public BrAPITrialCommit(BrAPICreationFactory brAPICreationFactory, BrAPIUpdateFactory brAPIUpdateFactory) {
        this.brAPICreationFactory = brAPICreationFactory;
        this.brAPIUpdateFactory = brAPIUpdateFactory;
    }
    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        try {
            brAPITrialCreation = brAPICreationFactory.trialWorkflowCreationBean(context);
            log.info("creating new trials in the BrAPI service");
            createdBrAPITrials = brAPITrialCreation.execute().map(s -> (WorkflowCreation.BrAPICreationState) s);
            brAPITrialUpdate = brAPIUpdateFactory.trialWorkflowUpdateBean(context);
            priorBrAPITrials = brAPITrialUpdate.getBrAPIState().map(s -> s);
            log.info("updating existing trials in the BrAPI service");
            updatedTrials = brAPITrialUpdate.execute().map(s -> (WorkflowUpdate.BrAPIUpdateState) s);

        } catch (ApiException e) {
            context.getAppendOverwriteWorkflowContext().setProcessError(new MiddlewareException(e));
            return this.compensate(context);
        }

        return processNext(context);
    }

    @Override
    public AppendOverwriteMiddlewareContext compensate(AppendOverwriteMiddlewareContext context) {
        // Tag an error if it occurred in this local transaction
        context.getAppendOverwriteWorkflowContext().getProcessError().tag(this.getClass().getName());

        // Delete any created trials from the BrAPI service
        createdBrAPITrials.ifPresent(WorkflowCreation.BrAPICreationState::undo);

        // Revert any changes made to trials in the BrAPI service
        priorBrAPITrials.ifPresent(WorkflowUpdate.BrAPIUpdateState::restore);

        // Undo the prior local transaction
        return compensatePrior(context);
    }
}
