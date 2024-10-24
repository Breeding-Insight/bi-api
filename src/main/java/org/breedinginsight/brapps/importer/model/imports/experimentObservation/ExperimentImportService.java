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

package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.brapps.importer.model.imports.DomainImportService;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentWorkflowNavigator;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class ExperimentImportService extends DomainImportService {

    @Inject
    public ExperimentImportService(ExperimentWorkflowNavigator workflowNavigator)
    {
        super(workflowNavigator);
    }

    @Override
    public ExperimentObservation getImportClass() {
        return new ExperimentObservation();
    }

    @Override
    public String getImportTypeId() {
        return "ExperimentImport";
    }

}

