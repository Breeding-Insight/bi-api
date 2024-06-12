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

import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.*;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.model.workflow.ProcessedData;
import org.breedinginsight.brapps.importer.services.processors.ProcessorData;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessContext;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.ProgramLocationService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class CommitPendingImportObjectsStep {

    private final BrAPIListDAO brAPIListDAO;
    private final BrAPITrialDAO brapiTrialDAO;
    private final BrAPIStudyDAO brAPIStudyDAO;
    private final BrAPIObservationDAO brAPIObservationDAO;
    private final BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    private final ProgramLocationService locationService;
    private final OntologyService ontologyService;

    @Inject
    public CommitPendingImportObjectsStep(BrAPIListDAO brAPIListDAO,
                                          BrAPITrialDAO brapiTrialDAO,
                                          BrAPIStudyDAO brAPIStudyDAO,
                                          BrAPIObservationDAO brAPIObservationDAO,
                                          BrAPIObservationUnitDAO brAPIObservationUnitDAO,
                                          ProgramLocationService locationService,
                                          OntologyService ontologyService) {
        this.brAPIListDAO = brAPIListDAO;
        this.brapiTrialDAO = brapiTrialDAO;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.brAPIObservationDAO = brAPIObservationDAO;
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
        this.locationService = locationService;
        this.ontologyService = ontologyService;
    }

    // TODO: some common code between workflows here that could be broken out, removed append/update specific code
    public void process(ProcessContext processContext, ProcessedData processedData) {

        PendingData pendingData = processContext.getPendingData();
        ImportContext importContext = processContext.getImportContext();

        ImportUpload upload = importContext.getUpload();
        Program program = importContext.getProgram();

        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = pendingData.getTrialByNameNoScope();
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = pendingData.getStudyByNameNoScope();
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = pendingData.getObsVarDatasetByName();
        Map<String, PendingImportObject<ProgramLocation>> locationByName = pendingData.getLocationByName();
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = pendingData.getObservationUnitByNameNoScope();
        Map<String, PendingImportObject<BrAPIObservation>> observationByHash = pendingData.getObservationByHash();

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
        List<BrAPIObservation> newObservations = ProcessorData.getNewObjects(observationByHash)
                .stream()
                .filter(obs -> !obs.getValue().isBlank())
                .collect(Collectors.toList());

        AuthenticatedUser actingUser = new AuthenticatedUser(upload.getUpdatedByUser().getName(), new ArrayList<>(), upload.getUpdatedByUser().getId(), new ArrayList<>());

        try {
            List<BrAPIListSummary> createdDatasets = new ArrayList<>(brAPIListDAO.createBrAPILists(newDatasetRequests, program.getId(), upload));
            createdDatasets.forEach(summary -> obsVarDatasetByName.get(summary.getListName()).getBrAPIObject().setListDbId(summary.getListDbId()));

            List<BrAPITrial> createdTrials = new ArrayList<>(brapiTrialDAO.createBrAPITrials(newTrials, program.getId(), upload));
            // set the DbId to the for each newly created trial
            for (BrAPITrial createdTrial : createdTrials) {
                String createdTrialName = Utilities.removeProgramKey(createdTrial.getTrialName(), program.getKey());
                trialByNameNoScope.get(createdTrialName)
                        .getBrAPIObject()
                        .setTrialDbId(createdTrial.getTrialDbId());
            }

            List<ProgramLocation> createdLocations = new ArrayList<>(locationService.create(actingUser, program.getId(), newLocations));
            // set the DbId to the for each newly created location
            for (ProgramLocation createdLocation : createdLocations) {
                String createdLocationName = createdLocation.getName();
                locationByName.get(createdLocationName)
                        .getBrAPIObject()
                        .setLocationDbId(createdLocation.getLocationDbId());
            }

            updateStudyDependencyValues(mappedBrAPIImport, program.getKey());
            List<BrAPIStudy> createdStudies = brAPIStudyDAO.createBrAPIStudies(newStudies, program.getId(), upload);

            // set the DbId to the for each newly created study
            for (BrAPIStudy createdStudy : createdStudies) {
                String createdStudy_name_no_key = Utilities.removeProgramKeyAndUnknownAdditionalData(createdStudy.getStudyName(), program.getKey());
                studyByNameNoScope.get(createdStudy_name_no_key)
                        .getBrAPIObject()
                        .setStudyDbId(createdStudy.getStudyDbId());
            }

            updateObsUnitDependencyValues(pendingData, program.getKey());
            List<BrAPIObservationUnit> createdObservationUnits = brAPIObservationUnitDAO.createBrAPIObservationUnits(newObservationUnits, program.getId(), upload);

            // set the DbId to the for each newly created Observation Unit
            for (BrAPIObservationUnit createdObservationUnit : createdObservationUnits) {
                // retrieve the BrAPI ObservationUnit from this.observationUnitByNameNoScope
                String createdObservationUnit_StripedStudyName = Utilities.removeProgramKeyAndUnknownAdditionalData(createdObservationUnit.getStudyName(), program.getKey());
                String createdObservationUnit_StripedObsUnitName = Utilities.removeProgramKeyAndUnknownAdditionalData(createdObservationUnit.getObservationUnitName(), program.getKey());
                String createdObsUnit_key = ExperimentUtilities.createObservationUnitKey(createdObservationUnit_StripedStudyName, createdObservationUnit_StripedObsUnitName);
                observationUnitByNameNoScope.get(createdObsUnit_key)
                        .getBrAPIObject()
                        .setObservationUnitDbId(createdObservationUnit.getObservationUnitDbId());
            }

            updateObservationDependencyValues(pendingData, program);
            brAPIObservationDAO.createBrAPIObservations(newObservations, program.getId(), upload);
        } catch (ApiException e) {
            log.error("Error saving experiment import: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException("Error saving experiment import", e);
        } catch (Exception e) {
            log.error("Error saving experiment import", e);
            throw new InternalServerException(e.getMessage(), e);
        }

        // NOTE: removed mutated trials code

        datasetNewDataById.forEach((id, dataset) -> {
            try {
                List<String> existingObsVarIds = brAPIListDAO.getListById(id, program.getId()).getResult().getData();
                List<String> newObsVarIds = dataset
                        .getData()
                        .stream()
                        .filter(obsVarId -> !existingObsVarIds.contains(obsVarId)).collect(Collectors.toList());
                List<String> obsVarIds = new ArrayList<>(existingObsVarIds);
                obsVarIds.addAll(newObsVarIds);
                dataset.setData(obsVarIds);
                brAPIListDAO.updateBrAPIList(id, dataset, program.getId());
            } catch (ApiException e) {
                log.error("Error updating dataset observation variables: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException("Error saving experiment import", e);
            } catch (Exception e) {
                log.error("Error updating dataset observation variables: ", e);
                throw new InternalServerException(e.getMessage(), e);
            }
        });

        // NOTE: removed mutated observations code

    }

    private void updateStudyDependencyValues(PendingData pendingData, Map<Integer, PendingImport> mappedBrAPIImport, String programKey) {
        // update location DbIds in studies for all distinct locations
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = pendingData.getTrialByNameNoScope();

        mappedBrAPIImport.values()
                .stream()
                .map(PendingImport::getLocation)
                .forEach(location -> updateStudyLocationDbId(pendingData, location));

        // update trial DbIds in studies for all distinct trials
        trialByNameNoScope.values()
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(trial -> updateTrialDbId(pendingData, trial, programKey));
    }

    private void updateTrialDbId(PendingData pendingData, BrAPITrial trial, String programKey) {
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = pendingData.getStudyByNameNoScope();

        studyByNameNoScope.values()
                .stream()
                .filter(study -> study.getBrAPIObject()
                        .getTrialName()
                        .equals(Utilities.removeProgramKey(trial.getTrialName(), programKey)))
                .forEach(study -> study.getBrAPIObject()
                        .setTrialDbId(trial.getTrialDbId()));
    }

    private void updateStudyLocationDbId(PendingData pendingData, PendingImportObject<ProgramLocation> location) {
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = pendingData.getStudyByNameNoScope();

        studyByNameNoScope.values()
                .stream()
                .filter(study -> location.getId().toString()
                        .equals(study.getBrAPIObject()
                                .getLocationDbId()))
                .forEach(study -> study.getBrAPIObject()
                        .setLocationDbId(location.getBrAPIObject().getLocationDbId()));
    }

    private void updateObsUnitDependencyValues(PendingData pendingData, String programKey) {
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = pendingData.getStudyByNameNoScope();
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = pendingData.getExistingGermplasmByGID();

        // update study DbIds
        studyByNameNoScope.values()
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(study -> updateStudyDbId(pendingData, study, programKey));

        // update germplasm DbIds
        existingGermplasmByGID.values()
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(germplasm -> updateGermplasmDbId(pendingData, germplasm));
    }

    private void updateGermplasmDbId(PendingData pendingData, BrAPIGermplasm germplasm) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = pendingData.getObservationUnitByNameNoScope();

        observationUnitByNameNoScope.values()
                .stream()
                .filter(obsUnit -> germplasm.getAccessionNumber() != null &&
                        germplasm.getAccessionNumber().equals(obsUnit
                                .getBrAPIObject()
                                .getAdditionalInfo().getAsJsonObject()
                                .get(BrAPIAdditionalInfoFields.GID).getAsString()))
                .forEach(obsUnit -> obsUnit.getBrAPIObject()
                        .setGermplasmDbId(germplasm.getGermplasmDbId()));
    }

    private void updateStudyDbId(PendingData pendingData, BrAPIStudy study, String programKey) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = pendingData.getObservationUnitByNameNoScope();

        observationUnitByNameNoScope.values()
                .stream()
                .filter(obsUnit -> obsUnit.getBrAPIObject()
                        .getStudyName()
                        .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(study.getStudyName(), programKey)))
                .forEach(obsUnit -> {
                    obsUnit.getBrAPIObject()
                            .setStudyDbId(study.getStudyDbId());
                    obsUnit.getBrAPIObject()
                            .setTrialDbId(study.getTrialDbId());
                });
    }


    private void updateObservationDependencyValues(PendingData pendingData, Program program) {
        String programKey = program.getKey();
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = pendingData.getObservationUnitByNameNoScope();
        Map<String, PendingImportObject<BrAPIObservation>> observationByHash = pendingData.getObservationByHash();

        // update the observations study DbIds, Observation Unit DbIds and Germplasm DbIds
        observationUnitByNameNoScope.values().stream()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(obsUnit -> updateObservationDbIds(pendingData, obsUnit, programKey));

        // Update ObservationVariable DbIds
        List<Trait> traits = getTraitList(program);
        CaseInsensitiveMap<String, Trait> traitMap = new CaseInsensitiveMap<>();
        for ( Trait trait: traits) {
            traitMap.put(trait.getObservationVariableName(),trait);
        }
        for (PendingImportObject<BrAPIObservation> observation : observationByHash.values()) {
            String observationVariableName = observation.getBrAPIObject().getObservationVariableName();
            if (observationVariableName != null && traitMap.containsKey(observationVariableName)) {
                String observationVariableDbId = traitMap.get(observationVariableName).getObservationVariableDbId();
                observation.getBrAPIObject().setObservationVariableDbId(observationVariableDbId);
            }
        }
    }

    // Update each ovservation's observationUnit DbId, study DbId, and germplasm DbId
    private void updateObservationDbIds(PendingData pendingData, BrAPIObservationUnit obsUnit, String programKey) {
        Map<String, PendingImportObject<BrAPIObservation>> observationByHash = pendingData.getObservationByHash();

        // FILTER LOGIC: Match on Env and Exp Unit ID
        observationByHash.values()
                .stream()
                .filter(obs -> obs.getBrAPIObject()
                        .getAdditionalInfo() != null
                        && obs.getBrAPIObject()
                        .getAdditionalInfo()
                        .get(BrAPIAdditionalInfoFields.STUDY_NAME) != null
                        && obs.getBrAPIObject()
                        .getAdditionalInfo()
                        .get(BrAPIAdditionalInfoFields.STUDY_NAME)
                        .getAsString()
                        .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(obsUnit.getStudyName(), programKey))
                        && Utilities.removeProgramKeyAndUnknownAdditionalData(obs.getBrAPIObject().getObservationUnitName(), programKey)
                        .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(obsUnit.getObservationUnitName(), programKey))
                )
                .forEach(obs -> {
                    if (StringUtils.isBlank(obs.getBrAPIObject().getObservationUnitDbId())) {
                        obs.getBrAPIObject().setObservationUnitDbId(obsUnit.getObservationUnitDbId());
                    }
                    obs.getBrAPIObject().setStudyDbId(obsUnit.getStudyDbId());
                    obs.getBrAPIObject().setGermplasmDbId(obsUnit.getGermplasmDbId());
                });
    }

    private List<Trait> getTraitList(Program program) {
        try {
            return ontologyService.getTraitsByProgramId(program.getId(), true);
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            throw new InternalServerException(e.toString(), e);
        }
    }

}
