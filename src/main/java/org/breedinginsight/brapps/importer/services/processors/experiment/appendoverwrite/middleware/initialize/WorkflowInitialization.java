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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.initialize;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.BrAPIReadFactory;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action.WorkflowReadInitialization;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.MiddlewareException;
import org.breedinginsight.model.ProgramLocation;

import javax.inject.Inject;

@Slf4j
@Prototype
public class WorkflowInitialization extends AppendOverwriteMiddleware {
    WorkflowReadInitialization<BrAPIObservationUnit> brAPIObservationUnitReadWorkflowInitialization;
    WorkflowReadInitialization<BrAPITrial> brAPITrialReadWorkflowInitialization;
    WorkflowReadInitialization<BrAPIStudy> brAPIStudyReadWorkflowInitialization;
    WorkflowReadInitialization<ProgramLocation> locationReadWorkflowInitialization;
    WorkflowReadInitialization<BrAPIListDetails> brAPIDatasetReadWorkflowInitialization;
    WorkflowReadInitialization<BrAPIGermplasm> brAPIGermplasmReadWorkflowInitialization;
    BrAPIReadFactory brAPIReadFactory;

    @Inject
    public WorkflowInitialization(BrAPIReadFactory brAPIReadFactory) {
        this.brAPIReadFactory = brAPIReadFactory;
    }
    @Override
    public AppendOverwriteMiddlewareContext process(AppendOverwriteMiddlewareContext context) {
        brAPIObservationUnitReadWorkflowInitialization = brAPIReadFactory.observationUnitWorkflowReadInitializationBean(context);
        brAPITrialReadWorkflowInitialization = brAPIReadFactory.trialWorkflowReadInitializationBean(context);
        brAPIStudyReadWorkflowInitialization = brAPIReadFactory.studyWorkflowReadInitializationBean(context);
        locationReadWorkflowInitialization = brAPIReadFactory.locationWorkflowReadInitializationBean(context);
        brAPIDatasetReadWorkflowInitialization = brAPIReadFactory.datasetWorkflowReadInitializationBean(context);
        brAPIGermplasmReadWorkflowInitialization = brAPIReadFactory.germplasmWorkflowReadInitializationBean(context);

        log.debug("reading required BrAPI data from BrAPI service");
        try {
            brAPIObservationUnitReadWorkflowInitialization.execute();
            brAPITrialReadWorkflowInitialization.execute();
            brAPIStudyReadWorkflowInitialization.execute();
            locationReadWorkflowInitialization.execute();
            brAPIDatasetReadWorkflowInitialization.execute();
            brAPIGermplasmReadWorkflowInitialization.execute();
        } catch (ApiException e) {
            context.getAppendOverwriteWorkflowContext().setProcessError(new MiddlewareException(e));
            return this.compensate(context);
        }

        return processNext(context);
    }
}
