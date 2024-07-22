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

    /**
     * Creates a workflow for creating a BrAPI trial.
     *
     * @param context The AppendOverwriteMiddlewareContext containing the necessary information for the creation of the trial.
     * @param pendingEntityFactory The factory responsible for creating pending entities.
     * @return A WorkflowCreation instance for creating a BrAPITrial.
     */
    public static WorkflowCreation<BrAPITrial> trialWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                     PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPITrial>(pendingEntityFactory.pendingTrialBean(context));
    }

    /**
     * Creates a workflow for creating a BrAPI dataset.
     *
     * @param context The AppendOverwriteMiddlewareContext containing the necessary information for the creation of the dataset.
     * @param pendingEntityFactory The factory responsible for creating pending entities.
     * @return A WorkflowCreation instance for creating a BrAPIListDetails.
     */
    public static WorkflowCreation<BrAPIListDetails> datasetWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                             PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIListDetails>(pendingEntityFactory.pendingDatasetBean(context));
    }

    /**
     * Creates a workflow for creating a BrAPI study.
     *
     * @param context The AppendOverwriteMiddlewareContext containing the necessary information for the creation of the study.
     * @param pendingEntityFactory The factory responsible for creating pending entities.
     * @return A WorkflowCreation instance for creating a BrAPIStudy.
     */
    public static WorkflowCreation<BrAPIStudy> studyWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                     PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIStudy>(pendingEntityFactory.pendingStudyBean(context));
    }

    /**
     * Creates a workflow for creating a BrAPI observation.
     *
     * @param context The AppendOverwriteMiddlewareContext containing the necessary information for the creation of the observation.
     * @param pendingEntityFactory The factory responsible for creating pending entities.
     * @return A WorkflowCreation instance for creating a BrAPIObservation.
     */
    public static WorkflowCreation<BrAPIObservation> observationWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                                 PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIObservation>(pendingEntityFactory.pendingObservationBean(context));
    }

    /**
     * Creates a workflow for creating a BrAPI observation unit.
     *
     * @param context The AppendOverwriteMiddlewareContext containing the necessary information for the creation of the observation unit.
     * @param pendingEntityFactory The factory responsible for creating pending entities.
     * @return A WorkflowCreation instance for creating a BrAPIObservationUnit.
     */
    public static WorkflowCreation<BrAPIObservationUnit> observationUnitWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                                         PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<BrAPIObservationUnit>(pendingEntityFactory.pendingObservationUnitBean(context));
    }

    /**
     * Creates a workflow for creating a Program Location.
     *
     * @param context The AppendOverwriteMiddlewareContext containing the necessary information for the creation of the location.
     * @param pendingEntityFactory The factory responsible for creating pending entities.
     * @return A WorkflowCreation instance for creating a ProgramLocation.
     */
    public static WorkflowCreation<ProgramLocation> locationWorkflowCreation(AppendOverwriteMiddlewareContext context,
                                                                             PendingEntityFactory pendingEntityFactory) {
        return new WorkflowCreation<ProgramLocation>(pendingEntityFactory.pendingLocationBean(context));
    }

    /**
     * This method is a Spring bean that creates a prototype instance of a WorkflowCreation object for BrAPITrial entities.
     * The WorkflowCreation object is responsible for creating a workflow for a BrAPITrial entity.
     *
     * @param context The middleware context containing information required for creating the workflow.
     * @return A WorkflowCreation object specialized for BrAPITrial entities.
     */
    @Bean
    @Prototype
    public WorkflowCreation<BrAPITrial> trialWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return trialWorkflowCreation(context, pendingEntityFactory);
    }

    /**
     * This method creates a new instance of WorkflowCreation for handling dataset-related tasks within the BrAPIListDetails context.
     * The WorkflowCreation instance will be configured as a Prototype bean, meaning it will return a new instance each time it is requested.
     * The method takes an AppendOverwriteMiddlewareContext as input, which provides the necessary context for the workflow creation.
     * The method returns a WorkflowCreation instance for handling dataset creation operations within the BrAPIListDetails context.
     * @param context The AppendOverwriteMiddlewareContext providing the context for the workflow creation process.
     * @return A WorkflowCreation instance configured to handle dataset creation operations within the BrAPIListDetails context.
     */
    @Bean
    @Prototype
    public WorkflowCreation<BrAPIListDetails> datasetWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return datasetWorkflowCreation(context, pendingEntityFactory);
    }

    /**
     * This method creates a bean for creating a workflow for a BrAPI study.
     * The bean is of prototype scope, meaning that a new instance of the bean will be created every time it is requested.
     * The workflow creation is specific to BrAPIStudy type.
     *
     * @param context The AppendOverwriteMiddlewareContext object containing the context information for workflow creation.
     * @return a WorkflowCreation object specialized for BrAPIStudy, configured using the provided context and pendingEntityFactory.
     */
    @Bean
    @Prototype
    public WorkflowCreation<BrAPIStudy> studyWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return studyWorkflowCreation(context, pendingEntityFactory);
    }

    /**
     * This method creates a new instance of WorkflowCreation for BrAPIObservation objects based on the provided AppendOverwriteMiddlewareContext.
     * It is marked as a prototype bean, meaning a new instance will be created each time it is injected.
     *
     * @param context The AppendOverwriteMiddlewareContext containing the necessary information for the workflow creation.
     * @return A WorkflowCreation instance for BrAPIObservation objects.
     */
    @Bean
    @Prototype
    public WorkflowCreation<BrAPIObservation> observationWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return observationWorkflowCreation(context, pendingEntityFactory);
    }

    /**
     * This method is responsible for creating a new instance of WorkflowCreation for BrAPIObservationUnit.
     * It leverages the observationUnitWorkflowCreation method to initialize the WorkflowCreation object.
     *
     * @param context the AppendOverwriteMiddlewareContext object containing relevant context information
     * @return a new instance of WorkflowCreation<BrAPIObservationUnit> object
     *
     */
    @Bean
    @Prototype
    public WorkflowCreation<BrAPIObservationUnit> observationUnitWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return observationUnitWorkflowCreation(context, pendingEntityFactory);
    }

    /**
     * This method creates a new WorkflowCreation instance for handling ProgramLocation objects by appending or overwriting the entities.
     * The WorkflowCreation instance is specific to each invocation.
     *
     * @param context the AppendOverwriteMiddlewareContext object containing the context for workflow creation
     * @return a WorkflowCreation instance configured for handling ProgramLocation objects
     */
    @Bean
    @Prototype
    public WorkflowCreation<ProgramLocation> locationWorkflowCreationBean(AppendOverwriteMiddlewareContext context) {
        return locationWorkflowCreation(context, pendingEntityFactory);
    }

}
