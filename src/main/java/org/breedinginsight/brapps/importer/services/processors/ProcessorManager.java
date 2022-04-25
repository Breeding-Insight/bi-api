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

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.services.ImportStatusService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.ValidatorException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Prototype
@Slf4j
public class ProcessorManager {

    private List<Processor> processors;
    private Map<Integer, PendingImport> mappedBrAPIImport;
    private ImportStatusService statusService;
    private Map<String, ImportPreviewStatistics> statistics = new HashMap<>();

    @Inject
    public ProcessorManager(ImportStatusService statusService) {
        this.processors = new ArrayList<>();
        this.mappedBrAPIImport = new HashMap<>();
        this.statusService = statusService;
    }

    public ImportPreviewResponse process(List<BrAPIImport> importRows, List<Processor> processors, Program program, ImportUpload upload, User user, boolean commit) throws ValidatorException, ApiException {

        this.processors = processors;

        // check existing brapi objects and map data for each registered type
        for (Processor processor : processors) {
            statusService.updateMessage(upload, "Checking existing objects in brapi service and mapping data");
            processor.getExistingBrapiData(importRows, program);
            //Map<String, ImportPreviewStatistics> stats = processor.process(importRows, mappedBrAPIImport, program, user, commit);
            //statistics.putAll(stats);
        }

        ImportPreviewResponse response = new ImportPreviewResponse();
        response.setStatistics(statistics);
        List<PendingImport> mappedBrAPIImportList = new ArrayList<>(mappedBrAPIImport.values());
        response.setRows(mappedBrAPIImportList);

        if (!commit) {
            statusService.updateOk(upload);
            return response;
        } else {
            validateProcessorDependencies();
            postBrapiData(program);
        }

        statusService.finishUpload(upload, response, "Finished mapping data to brapi objects");

        return response;
    }

    private void validateProcessorDependencies() {
        for (Processor processor : processors) {
            try {
                processor.validateDependencies(mappedBrAPIImport);
            } catch (ValidatorException e) {
                // TODO: accumulate errors and return in response to UI
                log.error(e.getMessage());
            }
        }
    }

    private void postBrapiData(Program program) throws ValidatorException {

        // get total number of new brapi objects to create
        long totalObjects = 0;
        for (ImportPreviewStatistics stats : statistics.values()) {
            totalObjects += stats.getNewObjectCount();
        }

        //statusService.startUpload(totalObjects, "Starting upload to brapi service");

        //for (Processor processor : processors) {
            //statusService.updateMessage("Creating new objects in brapi service");
            //processor.postBrapiData(mappedBrAPIImport, program, statusService.getUpload());
        //}

       // statusService.finishUpload("Completed upload to brapi service");
    }

}
