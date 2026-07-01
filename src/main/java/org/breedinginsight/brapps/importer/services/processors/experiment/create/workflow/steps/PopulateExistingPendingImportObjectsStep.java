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

import com.google.gson.JsonArray;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.*;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.model.workflow.ImportContext;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.ExperimentStudyService;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.ExperimentTrialService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.services.ProgramLocationService;
import org.breedinginsight.utilities.DatasetUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

/**
 * References code common between workflows in shared services. DAO access is done directly in the
 * steps rather than another layer of services.
 */

@Singleton
@Slf4j
public class PopulateExistingPendingImportObjectsStep {

    private final BrAPIStudyDAO brAPIStudyDAO;
    private final ProgramLocationService locationService;
    private final BrAPIListDAO brAPIListDAO;
    private final BrAPIGermplasmDAO brAPIGermplasmDAO;
    private final ExperimentStudyService experimentStudyService;
    private final ExperimentTrialService experimentTrialService;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public PopulateExistingPendingImportObjectsStep(BrAPIObservationUnitDAO brAPIObservationUnitDAO,
                                                    BrAPIStudyDAO brAPIStudyDAO,
                                                    ProgramLocationService locationService,
                                                    BrAPIListDAO brAPIListDAO,
                                                    BrAPIGermplasmDAO brAPIGermplasmDAO,
                                                    ExperimentStudyService experimentStudyService,
                                                    ExperimentTrialService experimentTrialService) {
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.locationService = locationService;
        this.brAPIListDAO = brAPIListDAO;
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.experimentStudyService = experimentStudyService;
        this.experimentTrialService = experimentTrialService;
    }

    public ProcessContext process(ImportContext input) {

        List<ExperimentObservation> experimentImportRows = ExperimentUtilities.importRowsToExperimentObservations(input.getImportRows());
        Program program = input.getProgram();

        // Populate pending objects with existing status
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = new HashMap<>();
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = experimentTrialService.initializeTrialByNameNoScope(program, experimentImportRows);
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = initializeStudyByNameNoScope(program, trialByNameNoScope, experimentImportRows);
        // interesting we're using our data model instead of brapi for locations
        Map<String, PendingImportObject<ProgramLocation>> locationByName = initializeUniqueLocationNames(program, experimentImportRows);
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = initializeObsVarDatasetByName(program, experimentImportRows);
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = initializeExistingGermplasmByGID(program, experimentImportRows);
        Map<String, BrAPIObservation> existingObsByObsHash = new HashMap<>();
        Map<String, String> expUnitByTrialName = new HashMap<>();

        PendingData existing = PendingData.builder()
                .observationUnitByNameNoScope(observationUnitByNameNoScope)
                .trialByNameNoScope(trialByNameNoScope)
                .studyByNameNoScope(studyByNameNoScope)
                .locationByName(locationByName)
                .obsVarDatasetByName(obsVarDatasetByName)
                .existingGermplasmByGID(existingGermplasmByGID)
                .existingObsByObsHash(existingObsByObsHash)
                .observationByHash(new HashMap<>())
                .expUnitByTrialName(expUnitByTrialName)
                .build();

        return ProcessContext.builder()
                .importContext(input)
                .pendingData(existing)
                .build();
    }

    /**
     * Retrieves the PendingImportObject of a BrAPITrial based on the given list of ExperimentObservation and trialByNameNoScope map.
     *
     * @param experimentImportRows The list of ExperimentObservation objects.
     * @param trialByNameNoScope The map of trial names to PendingImportObject of BrAPITrial.
     * @return The Optional containing the PendingImportObject of BrAPITrial, or an empty Optional if no matching trial is found.
     */
    private Optional<PendingImportObject<BrAPITrial>> getTrialPIO(List<ExperimentObservation> experimentImportRows,
                                                                  Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope) {
        Optional<String> expTitle = experimentImportRows.stream()
                .filter(row -> StringUtils.isNotBlank(row.getExpTitle()))
                .map(ExperimentObservation::getExpTitle)
                .findFirst();

        if (expTitle.isEmpty() && trialByNameNoScope.keySet().stream().findFirst().isEmpty()) {
            return Optional.empty();
        }
        if(expTitle.isEmpty()) {
            expTitle = trialByNameNoScope.keySet().stream().findFirst();
        }

        return Optional.ofNullable(trialByNameNoScope.get(expTitle.get()));
    }

    /**
     * Initializes unique location names for a program.
     *
     * @param program The program object.
     * @param experimentImportRows A list of experiment observation objects for import.
     * @return A map of location names and their corresponding pending import objects.
     * @throws InternalServerException If there is an error fetching locations.
     */
    private Map<String, PendingImportObject<ProgramLocation>> initializeUniqueLocationNames(Program program,
                                                                                            List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<ProgramLocation>> locationByName = new HashMap<>();

        List<ProgramLocation> existingLocations;
        List<String> uniqueLocationNames = experimentImportRows.stream()
                .map(ExperimentObservation::getEnvLocation)
                .distinct()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        try {
            existingLocations = new ArrayList<>(locationService.getLocationsByName(uniqueLocationNames, program.getId()));
        } catch (ApiException e) {
            log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        existingLocations.forEach(existingLocation -> locationByName.put(existingLocation.getName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation, existingLocation.getId())));
        return locationByName;
    }

    /**
     * Initializes studies by name without scope.
     *
     * @param program The program object.
     * @param trialByNameNoScope A map of trial names with their corresponding pending import objects.
     * @param experimentImportRows A list of experiment observation objects.
     * @return A map of study names with their corresponding pending import objects.
     * @throws InternalServerException If there is an error while processing the method.
     */
    private Map<String, PendingImportObject<BrAPIStudy>> initializeStudyByNameNoScope(Program program,
                                                                                      Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope,
                                                                                      List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIStudy>> studyByName = new HashMap<>();
        if (trialByNameNoScope.size() != 1) {
            return studyByName;
        }


        List<BrAPIStudy> existingStudies;
        Optional<PendingImportObject<BrAPITrial>> trial = getTrialPIO(experimentImportRows, trialByNameNoScope);

        String expExRefId = Utilities.getExternalReference(trial.get()
                .getBrAPIObject()
                .getExternalReferences(), BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS)
                .map(BrAPIExternalReference::getReferenceId)
                .orElse(null);

        if (expExRefId == null) {
            String logMessage = "No exref found in trial to link trial to study";
            log.error(logMessage);
            throw new InternalServerException(logMessage);
        }

        try {
            // the 'trial' variable will never be "null".
            UUID experimentId = UUID.fromString(expExRefId);
            existingStudies = brAPIStudyDAO.getStudiesByExperimentID(experimentId, program);
            for (BrAPIStudy existingStudy : existingStudies) {
                experimentStudyService.processAndCacheStudy(existingStudy, program, BrAPIStudy::getStudyName, studyByName);
            }
        } catch (ApiException e) {
            log.error("Error fetching studies: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        } catch (Exception e) {
            log.error("Error processing studies: ", e);
            throw new InternalServerException(e.toString(), e);
        }

        return studyByName;
    }

  /**
   * Initializes observation variable dataset by name.
   *
   * @param program The program associated with the dataset.
   * @param experimentImportRows The list of experiment observation rows.
   * @return The map of observation variable dataset indexed by name.
   *
   * @throws InternalServerException
   */
    private Map<String, PendingImportObject<BrAPIListDetails>> initializeObsVarDatasetByName(Program program,
                                                                                             List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = new HashMap<>();
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = new HashMap<>();

        Optional<PendingImportObject<BrAPITrial>> trialPIO = getTrialPIO(experimentImportRows, trialByNameNoScope);

        if (trialPIO.isPresent() && !trialPIO.get().getBrAPIObject().getAdditionalInfo().getAsJsonArray(BrAPIAdditionalInfoFields.DATASETS).isEmpty()) {
            JsonArray datasetsJson = trialPIO.get().getBrAPIObject()
                    .getAdditionalInfo()
                    .getAsJsonArray(BrAPIAdditionalInfoFields.DATASETS);
            String datasetId = DatasetUtil.getTopLevelDatasetFromJson(datasetsJson).getId().toString();

            try {
                List<BrAPIListSummary> existingDatasets = brAPIListDAO
                        .getListsByTypeAndExternalRef(BrAPIListTypes.OBSERVATIONVARIABLES,
                                program.getId(),
                                String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()),
                                UUID.fromString(datasetId));
                if (existingDatasets == null || existingDatasets.isEmpty()) {
                    throw new InternalServerException("existing dataset summary not returned from brapi server");
                }
                BrAPIListDetails dataSetDetails = brAPIListDAO
                        .getListById(existingDatasets.get(0).getListDbId(), program.getId())
                        .getResult();
                processAndCacheObsVarDataset(dataSetDetails, obsVarDatasetByName);
            } catch (ApiException e) {
                log.error(Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }
        return obsVarDatasetByName;
    }

    /**
     * Process and cache an object of type BrAPIListDetails.
     *
     * @param existingList The existing list to be processed and cached
     * @param obsVarDatasetByName A map of ObsVarDatasets indexed by name (will be modified in place)
     *
     * @throws IllegalStateException
     */
    private void processAndCacheObsVarDataset(BrAPIListDetails existingList, Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName) {
        BrAPIExternalReference xref = Utilities.getExternalReference(existingList.getExternalReferences(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()))
                .orElseThrow(() -> new IllegalStateException("External references wasn't found for list (dbid): " + existingList.getListDbId()));
        obsVarDatasetByName.put(existingList.getListName(),
                new PendingImportObject<>(ImportObjectState.EXISTING, existingList, UUID.fromString(xref.getReferenceId())));
    }

    /**
     * Initializes existing germplasm objects by germplasm ID (GID).
     *
     * @param program The program object.
     * @param experimentImportRows A list of experiment observation objects.
     * @return A map of existing germplasm objects by germplasm ID.
     *
     * @throws InternalServerException
     */
    private Map<String, PendingImportObject<BrAPIGermplasm>> initializeExistingGermplasmByGID(Program program,
                                                                                              List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();

        List<BrAPIGermplasm> existingGermplasms;

        List<String> uniqueGermplasmGIDs = experimentImportRows.stream()
                .map(ExperimentObservation::getGid)
                .distinct()
                .collect(Collectors.toList());

        try {
            existingGermplasms = new ArrayList<>(getGermplasmByAccessionNumber(uniqueGermplasmGIDs, program.getId()));
        } catch (ApiException e) {
            log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        existingGermplasms.forEach(existingGermplasm -> {
            BrAPIExternalReference xref = Utilities.getExternalReference(existingGermplasm.getExternalReferences(), String.format("%s", BRAPI_REFERENCE_SOURCE))
                    .orElseThrow(() -> new IllegalStateException("External references wasn't found for germplasm (dbid): " + existingGermplasm.getGermplasmDbId()));
            existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm, UUID.fromString(xref.getReferenceId())));
        });
        return existingGermplasmByGID;
    }

    /**
     * Retrieves a list of germplasm with the specified accession numbers.
     *
     * @param germplasmAccessionNumbers The list of accession numbers to search for.
     * @param programId The ID of the program.
     * @return An ArrayList of BrAPIGermplasm objects that match the accession numbers.
     * @throws ApiException if there is an error retrieving the germplasm.
     */
    private ArrayList<BrAPIGermplasm> getGermplasmByAccessionNumber(
            List<String> germplasmAccessionNumbers,
            UUID programId) throws ApiException {
        List<BrAPIGermplasm> germplasmList = brAPIGermplasmDAO.getGermplasm(programId);
        ArrayList<BrAPIGermplasm> resultGermplasm = new ArrayList<>();
        // Search for accession number matches
        for (BrAPIGermplasm germplasm : germplasmList) {
            for (String accessionNumber : germplasmAccessionNumbers) {
                if (germplasm.getAccessionNumber()
                        .equals(accessionNumber)) {
                    resultGermplasm.add(germplasm);
                    break;
                }
            }
        }
        return resultGermplasm;
    }
}