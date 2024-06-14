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

package org.breedinginsight.brapps.importer.services.processors.experiment.append.workflow;

import io.micronaut.context.annotation.Prototype;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.model.workflow.ProcessedData;
import org.breedinginsight.brapps.importer.model.workflow.Workflow;

import javax.inject.Named;

/**
 * This class represents a workflow for appending and overwriting phenotypes. The bean name must match the appropriate
 * bean column value in the import_mapping_workflow db table
 */

@Prototype
@Named("AppendOverwritePhenotypesWorkflow")
public class AppendOverwritePhenotypesWorkflow implements Workflow {
    @Override
    public ImportPreviewResponse process(ImportContext context) {
        // TODO
        return null;
    }

    /**
     * Retrieves the name of the workflow. This is used for logging display purposes.
     *
     * @return the name of the workflow
     */
    @Override
    public String getName() {
        return "AppendOverwritePhenotypesWorkflow";
    }
}
