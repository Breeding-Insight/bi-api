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
package org.breedinginsight.brapps.importer.services.processors.experiment.create.workflow.steps;

import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.brapps.importer.model.workflow.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.ProcessorData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessContext;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class CommitPendingImportObjectsStep {

    public void process(ProcessContext processContext, ProcessedData processedData) {

        PendingData pendingData = processContext.getPendingData();

        List<BrAPITrial> newTrials = ProcessorData.getNewObjects(pendingData.getTrialByNameNoScope());

        List<ProgramLocationRequest> newLocations = ProcessorData.getNewObjects(pendingData.getLocationByName())
                .stream()
                .map(location -> ProgramLocationRequest.builder()
                        .name(location.getName())
                        .build())
                .collect(Collectors.toList());

        List<BrAPIStudy> newStudies = ProcessorData.getNewObjects(pendingData.getStudyByNameNoScope());

        List<BrAPIListNewRequest> newDatasetRequests = ProcessorData.getNewObjects(pendingData.getObsVarDatasetByName()).stream().map(details -> {
            BrAPIListNewRequest request = new BrAPIListNewRequest();
            request.setListName(details.getListName());
            request.setListType(details.getListType());
            request.setExternalReferences(details.getExternalReferences());
            request.setAdditionalInfo(details.getAdditionalInfo());
            request.data(details.getData());
            return request;
        }).collect(Collectors.toList());

        Map<String, BrAPIListDetails> datasetNewDataById = ProcessorData
                .getMutationsByObjectId(pendingData.getObsVarDatasetByName(), BrAPIListSummary::getListDbId);

        List<BrAPIObservationUnit> newObservationUnits = ProcessorData.getNewObjects(pendingData.getObservationUnitByNameNoScope());

        // filter out observations with no 'value' so they will not be saved
        List<BrAPIObservation> newObservations = ProcessorData.getNewObjects(this.observationByHash)
                .stream()
                .filter(obs -> !obs.getValue().isBlank())
                .collect(Collectors.toList());

    }
}
