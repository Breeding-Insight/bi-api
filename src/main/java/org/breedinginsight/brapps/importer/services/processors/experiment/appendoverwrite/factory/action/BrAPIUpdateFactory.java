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
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;

import javax.inject.Inject;

@Factory
public class BrAPIUpdateFactory {
    private final PendingEntityFactory pendingEntityFactory;

    @Inject
    public BrAPIUpdateFactory(PendingEntityFactory pendingEntityFactory) {
        this.pendingEntityFactory = pendingEntityFactory;
    }

    public static WorkflowUpdate<BrAPITrial> trialWorkflowUpdate(AppendOverwriteMiddlewareContext context,
                                                                 PendingEntityFactory pendingEntityFactory) {
        return new WorkflowUpdate<BrAPITrial>(pendingEntityFactory.pendingTrialBean(context));
    }

    public static WorkflowUpdate<BrAPIObservation> observationWorkflowUpdate(AppendOverwriteMiddlewareContext context,
                                                                             PendingEntityFactory pendingEntityFactory) {
        return new WorkflowUpdate<BrAPIObservation>(pendingEntityFactory.pendingObservationBean(context));
    }

    public static WorkflowUpdate<BrAPIListDetails> datasetWorkflowUpdate(AppendOverwriteMiddlewareContext context,
                                                                         PendingEntityFactory pendingEntityFactory) {
        return new WorkflowUpdate<BrAPIListDetails>(pendingEntityFactory.pendingDatasetBean(context));
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
}
