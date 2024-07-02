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
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessedPhenotypeData;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.ExperimentStudyService;
import org.breedinginsight.brapps.importer.services.processors.experiment.services.ExperimentTrialService;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.ProgramLocationService;
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

    private final BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    private final BrAPITrialDAO brAPITrialDAO;
    private final BrAPIStudyDAO brAPIStudyDAO;
    private final ProgramLocationService locationService;
    private final BrAPIListDAO brAPIListDAO;
    private final BrAPIGermplasmDAO brAPIGermplasmDAO;
    private final BrAPIObservationDAO brAPIObservationDAO;
    private final ExperimentStudyService experimentStudyService;
    private final ExperimentTrialService experimentTrialService;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public PopulateExistingPendingImportObjectsStep(BrAPIObservationUnitDAO brAPIObservationUnitDAO,
                                                    BrAPITrialDAO brAPITrialDAO,
                                                    BrAPIStudyDAO brAPIStudyDAO,
                                                    ProgramLocationService locationService,
                                                    BrAPIListDAO brAPIListDAO,
                                                    BrAPIGermplasmDAO brAPIGermplasmDAO,
                                                    BrAPIObservationDAO brAPIObservationDAO,
                                                    ExperimentStudyService experimentStudyService,
                                                    ExperimentTrialService experimentTrialService) {
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
        this.brAPITrialDAO = brAPITrialDAO;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.locationService = locationService;
        this.brAPIListDAO = brAPIListDAO;
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.brAPIObservationDAO = brAPIObservationDAO;
        this.experimentStudyService = experimentStudyService;
        this.experimentTrialService = experimentTrialService;
    }

    public ProcessContext process(ImportContext input, ProcessedPhenotypeData phenotypeData) {

        List<ExperimentObservation> experimentImportRows = ExperimentUtilities.importRowsToExperimentObservations(input.getImportRows());
        Program program = input.getProgram();

        // Populate pending objects with existing status
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = initializeObservationUnits(program, experimentImportRows);
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = experimentTrialService.initializeTrialByNameNoScope(program, observationUnitByNameNoScope, experimentImportRows);
        Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope = initializeStudyByNameNoScope(program, trialByNameNoScope, observationUnitByNameNoScope, experimentImportRows);
        // interesting we're using our data model instead of brapi for locations
        Map<String, PendingImportObject<ProgramLocation>> locationByName = initializeUniqueLocationNames(program, studyByNameNoScope, experimentImportRows);
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = initializeObsVarDatasetByName(program, trialByNameNoScope, experimentImportRows);
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = initializeExistingGermplasmByGID(program, observationUnitByNameNoScope, experimentImportRows);
        Map<String, BrAPIObservation> existingObsByObsHash = fetchExistingObservations(phenotypeData.getReferencedTraits(), studyByNameNoScope, program);

        PendingData existing = PendingData.builder()
                .observationUnitByNameNoScope(observationUnitByNameNoScope)
                .trialByNameNoScope(trialByNameNoScope)
                .studyByNameNoScope(studyByNameNoScope)
                .locationByName(locationByName)
                .obsVarDatasetByName(obsVarDatasetByName)
                .existingGermplasmByGID(existingGermplasmByGID)
                .existingObsByObsHash(existingObsByObsHash)
                .observationByHash(new HashMap<>())
                .build();

        return ProcessContext.builder()
                .importContext(input)
                .pendingData(existing)
                .build();
    }

    /**
     * Initializes the observation units for the given program and experimentImportRows.
     *
     * @param program The program object
     * @param experimentImportRows A list of ExperimentObservation objects
     * @return A map of Observation Unit IDs to PendingImportObject<BrAPIObservationUnit> objects
     *
     * @throws InternalServerException
     * @throws IllegalStateException
     */
    private Map<String, PendingImportObject<BrAPIObservationUnit>> initializeObservationUnits(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByName = new HashMap<>();

        Map<String, ExperimentObservation> rowByObsUnitId = new HashMap<>();
        experimentImportRows.forEach(row -> {
            if (StringUtils.isNotBlank(row.getObsUnitID())) {
                if(rowByObsUnitId.containsKey(row.getObsUnitID())) {
                    throw new IllegalStateException("ObsUnitId is repeated: " + row.getObsUnitID());
                }
                rowByObsUnitId.put(row.getObsUnitID(), row);
            }
        });

        try {
            List<BrAPIObservationUnit> existingObsUnits = brAPIObservationUnitDAO.getObservationUnitsById(rowByObsUnitId.keySet(), program);

            // TODO: grab from externalReferences
            /*
            observationUnitByObsUnitId = existingObsUnits.stream()
                    .collect(Collectors.toMap(BrAPIObservationUnit::getObservationUnitDbId,
                            (BrAPIObservationUnit unit) -> new PendingImportObject<>(unit, false)));
             */

            String refSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());
            if (existingObsUnits.size() == rowByObsUnitId.size()) {
                existingObsUnits.forEach(brAPIObservationUnit -> {
                    processAndCacheObservationUnit(brAPIObservationUnit, refSource, program, observationUnitByName, rowByObsUnitId);

                    BrAPIExternalReference idRef = Utilities.getExternalReference(brAPIObservationUnit.getExternalReferences(), refSource)
                            .orElseThrow(() -> new InternalServerException("An ObservationUnit ID was not found in any of the external references"));

                    ExperimentObservation row = rowByObsUnitId.get(idRef.getReferenceId());
                    row.setExpTitle(Utilities.removeProgramKey(brAPIObservationUnit.getTrialName(), program.getKey()));
                    row.setEnv(Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIObservationUnit.getStudyName(), program.getKey()));
                    row.setEnvLocation(Utilities.removeProgramKey(brAPIObservationUnit.getLocationName(), program.getKey()));
                });
            } else {
                List<String> missingIds = new ArrayList<>(rowByObsUnitId.keySet());
                missingIds.removeAll(existingObsUnits.stream().map(BrAPIObservationUnit::getObservationUnitDbId).collect(Collectors.toList()));
                throw new IllegalStateException("Observation Units not found for ObsUnitId(s): " + String.join(ExperimentUtilities.COMMA_DELIMITER, missingIds));
            }

            return observationUnitByName;
        } catch (ApiException e) {
            log.error("Error fetching observation units: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }
    }

    /**
     * Adds a new map entry to observationUnitByName based on the brAPIObservationUnit passed in and sets the
     * expUnitId in the rowsByObsUnitId map.
     *
     * @param brAPIObservationUnit the BrAPI observation unit object
     * @param refSource the reference source
     * @param program the program object
     * @param observationUnitByName the map of observation units by name (will be modified in place)
     * @param rowByObsUnitId the map of rows by observation unit ID (will be modified in place)
     *
     * @throws InternalServerException
     */
    private void processAndCacheObservationUnit(BrAPIObservationUnit brAPIObservationUnit, String refSource, Program program,
                                                Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByName,
                                                Map<String, ExperimentObservation> rowByObsUnitId) {
        BrAPIExternalReference idRef = Utilities.getExternalReference(brAPIObservationUnit.getExternalReferences(), refSource)
                .orElseThrow(() -> new InternalServerException("An ObservationUnit ID was not found in any of the external references"));

        ExperimentObservation row = rowByObsUnitId.get(idRef.getReferenceId());
        row.setExpUnitId(Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIObservationUnit.getObservationUnitName(), program.getKey()));
        observationUnitByName.put(ExperimentUtilities.createObservationUnitKey(row),
                new PendingImportObject<>(ImportObjectState.EXISTING,
                        brAPIObservationUnit,
                        UUID.fromString(idRef.getReferenceId())));
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
                                                                                      Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope,
                                                                                      List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIStudy>> studyByName = new HashMap<>();
        if (trialByNameNoScope.size() != 1) {
            return studyByName;
        }

        try {
            initializeStudiesForExistingObservationUnits(program, studyByName, observationUnitByNameNoScope);
        } catch (ApiException e) {
            log.error("Error fetching studies: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        } catch (Exception e) {
            log.error("Error processing studies", e);
            throw new InternalServerException(e.toString(), e);
        }

        List<BrAPIStudy> existingStudies;
        Optional<PendingImportObject<BrAPITrial>> trial = getTrialPIO(experimentImportRows, trialByNameNoScope);

        try {
            if (trial.isEmpty()) {
                // TODO: throw ValidatorException and return 422
            }
            UUID experimentId = trial.get().getId();
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
     * Retrieves the PendingImportObject of a BrAPITrial based on the given list of ExperimentObservation and trialByNameNoScope map.
     *
     * @param experimentImportRows The list of ExperimentObservation objects.
     * @param trialByNameNoScope The map of trial names to PendingImportObject of BrAPITrial.
     * @return The Optional containing the PendingImportObject of BrAPITrial, or an empty Optional if no matching trial is found.
     */
    private Optional<PendingImportObject<BrAPITrial>> getTrialPIO(List<ExperimentObservation> experimentImportRows,
                                                                  Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope) {
        Optional<String> expTitle = experimentImportRows.stream()
                .filter(row -> StringUtils.isBlank(row.getObsUnitID()) && StringUtils.isNotBlank(row.getExpTitle()))
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


    private void initializeStudiesForExistingObservationUnits(
            Program program,
            Map<String, PendingImportObject<BrAPIStudy>> studyByName,
            Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope
    ) throws Exception {
        Set<String> studyDbIds = observationUnitByNameNoScope.values()
                .stream()
                .map(pio -> pio.getBrAPIObject()
                        .getStudyDbId())
                .collect(Collectors.toSet());

        List<BrAPIStudy> studies = experimentStudyService.fetchStudiesByDbId(studyDbIds, program);
        for (BrAPIStudy study : studies) {
            experimentStudyService.processAndCacheStudy(study, program, BrAPIStudy::getStudyName, studyByName);
        }
    }

    /**
     * Initializes unique location names for a program.
     *
     * @param program The program object.
     * @param studyByNameNoScope A map of study names and corresponding BrAPI study objects.
     * @param experimentImportRows A list of experiment observation objects for import.
     * @return A map of location names and their corresponding pending import objects.
     * @throws InternalServerException If there is an error fetching locations.
     */
    private Map<String, PendingImportObject<ProgramLocation>> initializeUniqueLocationNames(Program program,
                                                                                            Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope,
                                                                                            List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<ProgramLocation>> locationByName = new HashMap<>();

        List<ProgramLocation> existingLocations = new ArrayList<>();
        if(studyByNameNoScope.size() > 0) {
            Set<String> locationDbIds = studyByNameNoScope.values()
                    .stream()
                    .map(study -> study.getBrAPIObject()
                            .getLocationDbId())
                    .collect(Collectors.toSet());
            try {
                existingLocations.addAll(locationService.getLocationsByDbId(locationDbIds, program.getId()));
            } catch (ApiException e) {
                log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }

        List<String> uniqueLocationNames = experimentImportRows.stream()
                .filter(experimentObservation -> StringUtils.isBlank(experimentObservation.getObsUnitID()))
                .map(ExperimentObservation::getEnvLocation)
                .distinct()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        try {
            existingLocations.addAll(locationService.getLocationsByName(uniqueLocationNames, program.getId()));
        } catch (ApiException e) {
            log.error("Error fetching locations: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        existingLocations.forEach(existingLocation -> locationByName.put(existingLocation.getName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingLocation, existingLocation.getId())));
        return locationByName;
    }

    /**
     * Initializes observation variable dataset by name.
     *
     * @param program The program associated with the dataset.
     * @param trialByNameNoScope The map of trials identified by name without scope.
     * @param experimentImportRows The list of experiment observation rows.
     * @return The map of observation variable dataset indexed by name.
     *
     * @throws InternalServerException
     */
    private Map<String, PendingImportObject<BrAPIListDetails>> initializeObsVarDatasetByName(Program program,
                                                                                             Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope,
                                                                                             List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIListDetails>> obsVarDatasetByName = new HashMap<>();

        Optional<PendingImportObject<BrAPITrial>> trialPIO = getTrialPIO(experimentImportRows, trialByNameNoScope);

        if (trialPIO.isPresent() && trialPIO.get().getBrAPIObject().getAdditionalInfo().has(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)) {
            String datasetId = trialPIO.get().getBrAPIObject()
                    .getAdditionalInfo()
                    .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID)
                    .getAsString();
            try {
                List<BrAPIListSummary> existingDatasets = brAPIListDAO
                        .getListByTypeAndExternalRef(BrAPIListTypes.OBSERVATIONVARIABLES,
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
     * @param observationUnitByNameNoScope A map of observation unit objects by name.
     * @param experimentImportRows A list of experiment observation objects.
     * @return A map of existing germplasm objects by germplasm ID.
     *
     * @throws InternalServerException
     */
    private Map<String, PendingImportObject<BrAPIGermplasm>> initializeExistingGermplasmByGID(Program program,
                                                                                              Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope,
                                                                                              List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();

        List<BrAPIGermplasm> existingGermplasms = new ArrayList<>();
        if(observationUnitByNameNoScope.size() > 0) {
            Set<String> germplasmDbIds = observationUnitByNameNoScope.values().stream().map(ou -> ou.getBrAPIObject().getGermplasmDbId()).collect(Collectors.toSet());
            try {
                existingGermplasms.addAll(brAPIGermplasmDAO.getGermplasmsByDBID(germplasmDbIds, program.getId()));
            } catch (ApiException e) {
                log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }

        List<String> uniqueGermplasmGIDs = experimentImportRows.stream()
                .filter(experimentObservation -> StringUtils.isBlank(experimentObservation.getObsUnitID()))
                .map(ExperimentObservation::getGid)
                .distinct()
                .collect(Collectors.toList());

        try {
            existingGermplasms.addAll(getGermplasmByAccessionNumber(uniqueGermplasmGIDs, program.getId()));
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

    /**
     * Fetches existing observations based on the given referenced traits, studyByNameNoScope map, and program.
     *
     * @param referencedTraits       The list of referenced traits.
     * @param studyByNameNoScope     The map of studies by name without scope.
     * @param program                The program.
     * @return A map of existing observations with their unique keys.
     */
    private Map<String, BrAPIObservation> fetchExistingObservations(List<Trait> referencedTraits,
                                                                    Map<String, PendingImportObject<BrAPIStudy>> studyByNameNoScope,
                                                                    Program program) {
        Set<String> ouDbIds = new HashSet<>();
        Set<String> variableDbIds = new HashSet<>();
        Map<String, String> variableNameByDbId = new HashMap<>();
        Map<String, String> ouNameByDbId = new HashMap<>();
        Map<String, String> studyNameByDbId = studyByNameNoScope.values()
                .stream()
                .filter(pio -> StringUtils.isNotBlank(pio.getBrAPIObject().getStudyDbId()))
                .map(PendingImportObject::getBrAPIObject)
                .collect(Collectors.toMap(BrAPIStudy::getStudyDbId, brAPIStudy -> Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIStudy.getStudyName(), program.getKey())));

        studyNameByDbId.keySet().forEach(studyDbId -> {
            try {
                brAPIObservationUnitDAO.getObservationUnitsForStudyDbId(studyDbId, program).forEach(ou -> {
                    if(StringUtils.isNotBlank(ou.getObservationUnitDbId())) {
                        ouDbIds.add(ou.getObservationUnitDbId());
                    }
                    ouNameByDbId.put(ou.getObservationUnitDbId(), Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()));
                });
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });

        for (Trait referencedTrait : referencedTraits) {
            variableDbIds.add(referencedTrait.getObservationVariableDbId());
            variableNameByDbId.put(referencedTrait.getObservationVariableDbId(), referencedTrait.getObservationVariableName());
        }

        List<BrAPIObservation> existingObservations = new ArrayList<>();
        try {
            existingObservations = brAPIObservationDAO.getObservationsByObservationUnitsAndVariables(ouDbIds, variableDbIds, program);
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }

        return existingObservations.stream()
                .map(obs -> {
                    String studyName = studyNameByDbId.get(obs.getStudyDbId());
                    String variableName = variableNameByDbId.get(obs.getObservationVariableDbId());
                    String ouName = ouNameByDbId.get(obs.getObservationUnitDbId());

                    String key = ExperimentUtilities.getObservationHash(ExperimentUtilities.createObservationUnitKey(studyName, ouName), variableName, studyName);

                    return Map.entry(key, obs);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}