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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.BrAPIState;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

@Factory
public class BrAPIUpdateFactory {
    private final PendingEntityFactory pendingEntityFactory;

    @Inject
    public BrAPIUpdateFactory(PendingEntityFactory pendingEntityFactory) {
        this.pendingEntityFactory = pendingEntityFactory;
    }

    private static WorkflowUpdate<BrAPITrial> trialWorkflowUpdate(AppendOverwriteMiddlewareContext context,
                                                                 PendingEntityFactory pendingEntityFactory) {
        return new WorkflowUpdate<>(pendingEntityFactory.pendingTrialBean(context));
    }

    private static WorkflowUpdate<BrAPIObservation> observationWorkflowUpdate(AppendOverwriteMiddlewareContext context,
                                                                             PendingEntityFactory pendingEntityFactory) {
        return new WorkflowUpdate<>(pendingEntityFactory.pendingObservationBean(context));
    }

    private static WorkflowUpdate<BrAPIListDetails> datasetWorkflowUpdate(AppendOverwriteMiddlewareContext context,
                                                                         PendingEntityFactory pendingEntityFactory) {
        return new WorkflowUpdate<>(pendingEntityFactory.pendingDatasetBean(context));
    }

    @Bean
    @Prototype
    public WorkflowUpdate<BrAPITrial> trialWorkflowUpdateBean(AppendOverwriteMiddlewareContext context) {
        return trialWorkflowUpdate(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowUpdate<BrAPIObservation> observationWorkflowUpdateBean(AppendOverwriteMiddlewareContext context) {
        return observationWorkflowUpdate(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowUpdate<BrAPIListDetails> datasetWorkflowUpdateBean(AppendOverwriteMiddlewareContext context) {
        return datasetWorkflowUpdate(context, pendingEntityFactory);
    }

    @Slf4j
    @Prototype
    public static class WorkflowUpdate<T> implements BrAPIAction<T> {
        private final ExperimentImportEntity<T> entity;

        private WorkflowUpdate(ExperimentImportEntity<T> entity) {

            this.entity = entity;
        }

        public Optional<BrAPIState<T>> execute() throws ApiException {
            return saveAndUpdateCache(entity.copyWorkflowMembers(ImportObjectState.MUTATED));
        }

        /**
         * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
         *
         * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
         */
        @Override
        public ExperimentImportEntity<T> getEntity() {
            return null;
        }

        public Optional<BrAPIUpdateState<T>> getBrAPIState() {
            try {
                return Optional.of(new BrAPIUpdateState<T>(entity.getBrAPIState(ImportObjectState.MUTATED)));
            } catch (ApiException e) {
                // TODO: add specific error messages to entity service
                log.error("Error getting...");
                throw new InternalServerException("Error getting...", e);
            }

        }
        protected <V> Optional<BrAPIState<V>> saveAndUpdateCache(List<V> members) throws IllegalArgumentException, ApiException {

            if (members == null) {
                throw new IllegalArgumentException("BrAPI entity cannot be null");
            }
            List<V> savedMembers = entity.brapiPut(members);
            entity.updateWorkflow(savedMembers);
            return Optional.of(new BrAPIUpdateState<V>(savedMembers));
        }


        @Getter
        public class BrAPIUpdateState<U> implements BrAPIState<U> {
            private final List<U> members;

            public BrAPIUpdateState(List<U> existingMembers) { this.members = existingMembers; }

            public boolean restore() throws ApiException {
                return saveAndUpdateCache(this.getMembers()).isPresent();
            }
        }

    }
}
