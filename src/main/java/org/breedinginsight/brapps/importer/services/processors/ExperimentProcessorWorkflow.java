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

package org.breedinginsight.brapps.importer.services.processors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;

import java.util.List;

@Slf4j
public class ExperimentProcessorWorkflow {

    public enum Workflows {
        UNSPECIFIED,
        CREATE_EXP_ENV_PHENOTYPES,
        APPEND_UPDATE_EXPUNIT_PHENOTYPES,
        APPEND_UPDATE_SUBOBSUNIT_PHENOTYPES
    }
    
    private Workflows workflow = Workflows.UNSPECIFIED;

    public ExperimentProcessorWorkflow() {
    }

    public void determineWorkflow(List<BrAPIImport> importRows) {

        boolean hasExpUnitObsUnitIDs = importRows.stream()
                .anyMatch(row -> {
                    ExperimentObservation expRow = (ExperimentObservation) row;
                    return StringUtils.isNotBlank(expRow.getObsUnitID());
                });

        if (hasExpUnitObsUnitIDs) {
            long distinctCount = importRows.stream()
                    .map(row -> {
                        ExperimentObservation expRow = (ExperimentObservation) row;
                        return expRow.getObsUnitID();
                    })
                    .distinct()
                    .count();

            if (distinctCount != importRows.size()) {
                // If have ExpUnit ObsUnitIDs and there are duplicates -> Append / Update SubObsUnit Phenotypes
                setWorkflow(Workflows.APPEND_UPDATE_SUBOBSUNIT_PHENOTYPES);
            } else {
                // If have ExpUnit ObsUnitIDs and all are unique -> Append / Update ExpUnit Phenotypes
                setWorkflow(Workflows.APPEND_UPDATE_EXPUNIT_PHENOTYPES);
            }

        } else {
            // No ObsUnitIDs so creating experiment or appending env
            setWorkflow(Workflows.CREATE_EXP_ENV_PHENOTYPES);
        }
    }

    public void setWorkflow(Workflows workflow) {
        log.debug("Workflow: " + workflow);
        this.workflow = workflow;
    }

    public Workflows getWorkflow() {
        return workflow;
    }

}