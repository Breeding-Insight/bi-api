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
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.entity.PendingEntityFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.model.ProgramLocation;

import javax.inject.Inject;

@Factory
public class BrAPICreationFactory {
    private final PendingEntityFactory pendingEntityFactory;

    @Inject
    public BrAPICreationFactory(PendingEntityFactory pendingEntityFactory) {
        this.pendingEntityFactory = pendingEntityFactory;
    }

    public static WorkflowCreation<BrAPITrial> trialWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                     PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPITrial>(pendingEntityFactory.pendingTrialBean(context));
    }

    public static WorkflowCreation<BrAPIListDetails> datasetWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                             PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIListDetails>(pendingEntityFactory.pendingDatasetBean(context));
    }

    public static WorkflowCreation<BrAPIStudy> studyWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                     PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIStudy>(pendingEntityFactory.pendingStudyBean(context));
    }

    public static WorkflowCreation<BrAPIObservation> observationWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                                 PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIObservation>(pendingEntityFactory.pendingObservationBean(context));
    }

    public static WorkflowCreation<BrAPIObservationUnit> observationUnitWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                                         PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIObservationUnit>(pendingEntityFactory.pendingObservationUnitBean(context));
    }

    public static WorkflowCreation<ProgramLocation> locationWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                             PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<ProgramLocation>(pendingEntityFactory.pendingLocationBean(context));
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPITrial> trialWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return trialWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPIListDetails> datasetWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return datasetWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPIStudy> studyWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return studyWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPIObservation> observationWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return observationWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<BrAPIObservationUnit> observationUnitWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return observationUnitWorkflowCreation(context, pendingEntityFactory);
    }

    @Bean
    @Prototype
    public WorkflowCreation<ProgramLocation> locationWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return locationWorkflowCreation(context, pendingEntityFactory);
    }
}
