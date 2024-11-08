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

package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.COMMA_DELIMITER;

@Singleton
@Slf4j
public class TrialService {
    private final BrAPITrialDAO brAPITrialDAO;

    private final StudyService studyService;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public TrialService(BrAPITrialDAO brAPITrialDAO,
                        StudyService studyService) {
        this.brAPITrialDAO = brAPITrialDAO;
        this.studyService = studyService;
    }
    /**
     * Module: BrAPITrialService
     * Description: This module contains methods for retrieving BrAPI trials based on trial database IDs and programs.
     * brAPITrialDAO: Data Access Object for interacting with BrAPI trials in the database.
     * fetchBrapiTrialsByDbId: Method to fetch BrAPI trials based on provided trial database IDs and program.
     */

    /**
     * Retrieves the TrialDbId belonging to a pending unit based on the provided BrAPI observation unit and program.
     * If the TrialDbId is directly assigned to the unit, it is returned. Otherwise, the TrialDbId
     * assigned to the study belonging to the unit is retrieved.
     *
     * @param pendingUnit The pending import object representing the BrAPI observation unit.
     * @param program The program associated with the pending import.
     * @return The TrialDbId belonging to the pending unit.
     * @throws IllegalStateException if TrialDbId and StudyDbId are not set for an existing ObservationUnit.
     * @throws InternalServerException if there is an internal server error while fetching the TrialDbId.
     */
    public String getTrialDbIdBelongingToPendingUnit(PendingImportObject<BrAPIObservationUnit> pendingUnit,
                                                               Program program) throws IllegalStateException, InternalServerException {
        String trialDbId = null;

        BrAPIObservationUnit brapiUnit = pendingUnit.getBrAPIObject();
        if (StringUtils.isBlank(brapiUnit.getTrialDbId()) && StringUtils.isBlank(brapiUnit.getStudyDbId())) {
            throw new IllegalStateException("TrialDbId and StudyDbId are not set for an existing ObservationUnit");
        }

        // get the trial directly assigned to the unit
        if (StringUtils.isNotBlank(brapiUnit.getTrialDbId())) {
            trialDbId = brapiUnit.getTrialDbId();
        } else {

            // or get the trial directly assigned to the study belonging to the unit
            String studyDbId = brapiUnit.getStudyDbId();
            try {
                trialDbId = getTrialDbIdBelongingToStudy(studyDbId, program);
            } catch (ApiException e) {
                log.error("Error fetching studies: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }

        return trialDbId;
    }

    /**
     * Fetches BrAPI trials belonging to the specified trial database IDs and program.
     *
     * This method retrieves a list of BrAPI trials that are associated with the provided set of trial database IDs
     * within the context of the given program. It ensures that all specified trial IDs are found in the result
     * to maintain data integrity.
     *
     * @param trialDbIds A set of trial database IDs indicating the trials to fetch.
     * @param program The program object representing the program to which the trials belong.
     * @return A list of BrAPITrial objects representing the fetched trials.
     * @throws IllegalStateException If not all specified trial database IDs are found in the fetched trials.
     * @throws InternalServerException If an error occurs during the API call to fetch the trials.
     */
    public List<BrAPITrial> fetchBrapiTrialsBelongingToUnits(Set<String> trialDbIds, Program program) {
        try {
            List<BrAPITrial> trials = brAPITrialDAO.getTrialsByDbIds(trialDbIds, program);
            if (trials.size() != trialDbIds.size()) {
                List<String> missingIds = new ArrayList<>(trialDbIds);
                missingIds.removeAll(trials.stream().map(BrAPITrial::getTrialDbId).collect(Collectors.toList()));
                throw new IllegalStateException("Trial not found for trialDbId(s): " + String.join(ExperimentUtilities.COMMA_DELIMITER, missingIds));
            }

            return trials;
        } catch (ApiException e) {
            log.error("Error fetching trials: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }
    }

    /**
     * Retrieves a list of BrAPI trials based on a set of trial database IDs and a specified program.
     *
     * @param trialDbIds a set of trial database IDs used to retrieve the BrAPI trials
     * @param program the program associated with the trials
     * @return a list of BrAPITrial objects that match the provided trial database IDs and program
     * @throws InternalServerException if there is an internal server error during the retrieval process
     * @throws ApiException if there is an exception while fetching the trials
     */
    public List<BrAPITrial> fetchBrapiTrialsByDbId(Set<String> trialDbIds, Program program) throws InternalServerException, ApiException {
        // Initialize the list of BrAPI trials
        List<BrAPITrial> brapiTrials = null;

        // Retrieve the trials from the DAO based on the provided trial database IDs and program
        brapiTrials = brAPITrialDAO.getTrialsByDbIds(trialDbIds, program);

        // Check if all requested trials were found
        if (trialDbIds.size() != brapiTrials.size()) {
            // Identify the missing trial database IDs
            Set<String> missingIds = new HashSet<>(trialDbIds);
            missingIds.removeAll(brapiTrials.stream().map(BrAPITrial::getTrialDbId).collect(Collectors.toSet()));

            // Throw an exception with the list of missing trial database IDs
            throw new IllegalStateException("Trial not found for trial dbid(s): " + String.join(COMMA_DELIMITER, missingIds));
        }

        // Return the list of retrieved BrAPI trials
        return brapiTrials;
    }


    // TODO: also used in other workflow
    public void initializeTrialsForExistingObservationUnits(ImportContext importContext,
                                                            PendingData pendingData) {
        if(pendingData.getObservationUnitByNameNoScope().size() > 0) {
            Set<String> trialDbIds = new HashSet<>();
            Set<String> studyDbIds = new HashSet<>();

            pendingData.getObservationUnitByNameNoScope().values()
                    .forEach(pio -> {
                        BrAPIObservationUnit existingOu = pio.getBrAPIObject();
                        if (StringUtils.isBlank(existingOu.getTrialDbId()) && StringUtils.isBlank(existingOu.getStudyDbId())) {
                            throw new IllegalStateException("TrialDbId and StudyDbId are not set for an existing ObservationUnit");
                        }

                        if (StringUtils.isNotBlank(existingOu.getTrialDbId())) {
                            trialDbIds.add(existingOu.getTrialDbId());
                        } else {
                            studyDbIds.add(existingOu.getStudyDbId());
                        }
                    });

            //if the OU doesn't have the trialDbId set, then fetch the study to fetch the trialDbId
            if(!studyDbIds.isEmpty()) {
                try {
                    trialDbIds.addAll(fetchTrialDbidsForStudies(studyDbIds, importContext.getProgram()));
                } catch (ApiException e) {
                    log.error("Error fetching studies: " + Utilities.generateApiExceptionLogMessage(e), e);
                    throw new InternalServerException(e.toString(), e);
                }
            }

            try {
                List<BrAPITrial> trials = brAPITrialDAO.getTrialsByDbIds(trialDbIds, importContext.getProgram());
                if (trials.size() != trialDbIds.size()) {
                    List<String> missingIds = new ArrayList<>(trialDbIds);
                    missingIds.removeAll(trials.stream().map(BrAPITrial::getTrialDbId).collect(Collectors.toList()));
                    throw new IllegalStateException("Trial not found for trialDbId(s): " + String.join(ExperimentUtilities.COMMA_DELIMITER, missingIds));
                }

            trials.forEach(trial -> processAndCacheTrial(trial, importContext.getProgram(), pendingData.getTrialByNameNoScope()));
            } catch (ApiException e) {
                log.error("Error fetching trials: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }
    }

    /**
     * Fetches trial DbIds for the given study DbIds by using the BrAPI studies API.
     *
     * @param studyDbIds The set of study DbIds for which to fetch trial DbIds.
     * @param program The program associated with the studies.
     * @return A set of trial DbIds corresponding to the provided study DbIds.
     * @throws ApiException If there was an error while fetching the studies or if a study does not have a trial DbId.
     * @throws IllegalStateException If the trial DbId is not set for an existing study.
     */
    private Set<String> fetchTrialDbidsForStudies(Set<String> studyDbIds, Program program) throws ApiException {
        Set<String> trialDbIds = new HashSet<>();
        List<BrAPIStudy> studies = studyService.fetchStudiesByDbId(studyDbIds, program);
        studies.forEach(study -> {
            if (StringUtils.isBlank(study.getTrialDbId())) {
                throw new IllegalStateException("TrialDbId is not set for an existing Study: " + study.getStudyDbId());
            }
            trialDbIds.add(study.getTrialDbId());
        });

        return trialDbIds;
    }

    /**
     * Retrieves the trialDbId that belongs to a specific study with the given studyDbId and program.
     *
     * @param studyDbId The unique identifier for the study in the system.
     * @param program The program object associated with the study.
     * @return The trialDbId associated with the study.
     * @throws ApiException if the study with the provided studyDbId is not found in the BrAPI service.
     * @throws IllegalStateException if the trialDbId is not set for the existing study.
     */
    private String getTrialDbIdBelongingToStudy(String studyDbId, Program program) throws ApiException {
        String trialDbId = null;
        List<BrAPIStudy> studies = studyService.fetchStudiesByDbId(Set.of(studyDbId), program);
        if (studies.size() == 0) {
            throw new ApiException("Study not found in BrAPI service: " + studyDbId);
        }
        BrAPIStudy study = studies.get(0);
        if (StringUtils.isBlank(study.getTrialDbId())) {
            throw new IllegalStateException("TrialDbId is not set for an existing Study: " + study.getStudyDbId());
        }
        trialDbId = study.getTrialDbId();

        return trialDbId;
    }

    /**
     * This method processes an existing trial, retrieves the experiment ID from the trial's external references,
     * and caches the trial with the corresponding experiment ID in a map.
     *
     * @param existingTrial The existing BrAPITrial object to be processed and cached.
     * @param program The Program object associated with the trial.
     * @param trialByNameNoScope The map to cache the trial by its name without program scope. (will be modified in place)
     *
     * @throws InternalServerException
     */
    private void processAndCacheTrial(
            BrAPITrial existingTrial,
            Program program,
            Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope) {

        //get TrialId from existingTrial
        BrAPIExternalReference experimentIDRef = Utilities.getExternalReference(existingTrial.getExternalReferences(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()))
                .orElseThrow(() -> new InternalServerException("An Experiment ID was not found in any of the external references"));
        UUID experimentId = UUID.fromString(experimentIDRef.getReferenceId());

        trialByNameNoScope.put(
                Utilities.removeProgramKey(existingTrial.getTrialName(), program.getKey()),
                new PendingImportObject<>(ImportObjectState.EXISTING, existingTrial, experimentId));
    }

    /**
     * Constructs a PendingImportObject containing a BrAPITrial object based on the input BrAPITrial.
     *
     * This function takes a BrAPITrial object as input and constructs a PendingImportObject which
     * encapsulates the trial along with its associated experiment ID. The experiment ID is retrieved
     * from the external references of the trial object using utility method getExternalReference.
     * If the experiment ID is not found in the external references, an InternalServerException is thrown.
     *
     * @param trial the BrAPITrial object for which the PendingImportObject is to be constructed
     * @return a PendingImportObject containing the BrAPITrial object and its associated experiment ID
     * @throws InternalServerException if the experiment ID is not found in the external references of the trial
     */
    public PendingImportObject<BrAPITrial> constructPIOFromBrapiTrial(BrAPITrial trial) throws InternalServerException {
        PendingImportObject<BrAPITrial> pio = null;
        BrAPIExternalReference experimentIDRef = Utilities.getExternalReference(trial.getExternalReferences(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS.getName()))
                .orElseThrow(() -> new InternalServerException("An Experiment ID was not found in any of the external references"));
        UUID experimentId = UUID.fromString(experimentIDRef.getReferenceId());
        pio = new PendingImportObject<>(ImportObjectState.EXISTING, trial, experimentId);
        return pio;
    }

    /**
     * Initializes trials by name without scope for the given program.
     *
     * @param program                   the program to initialize trials for
     * @param observationUnitByNameNoScope   a map of observation units by name without scope
     * @param experimentImportRows      a list of experiment observation rows
     * @return a map of trials by name with pending import objects
     *
     * @throws InternalServerException
     */
    private Map<String, PendingImportObject<BrAPITrial>> initializeTrialByNameNoScope(Program program, Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope,
                                                                                      List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPITrial>> trialByName = new HashMap<>();

        initializeTrialsForExistingObservationUnits(program, observationUnitByNameNoScope, trialByName);

        List<String> uniqueTrialNames = experimentImportRows.stream()
                .filter(row -> StringUtils.isBlank(row.getObsUnitID()))
                .map(ExperimentObservation::getExpTitle)
                .distinct()
                .collect(Collectors.toList());
        try {
            brAPITrialDAO.getTrialsByName(uniqueTrialNames, program).forEach(existingTrial ->
                    processAndCacheTrial(existingTrial, program, trialByName)
            );
        } catch (ApiException e) {
            log.error("Error fetching trials: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        return trialByName;
    }

    private void initializeTrialsForExistingObservationUnits(Program program, Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope, Map<String, PendingImportObject<BrAPITrial>> trialByName) {
    }

    // TODO: used by expunit workflow
    public Map<String, PendingImportObject<BrAPITrial>> mapPendingTrialByOUId(
            String unitId,
            BrAPIObservationUnit unit,
            Map<String, PendingImportObject<BrAPITrial>> trialByName,
            Map<String, PendingImportObject<BrAPIStudy>> studyByName,
            Map<String, PendingImportObject<BrAPITrial>> trialByOUId,
            Program program
    ) {
        String trialName;
        if (unit.getTrialName() != null) {
            trialName = Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getTrialName(), program.getKey());
        } else if (unit.getStudyName() != null) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getStudyName(), program.getKey());
            trialName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    studyByName.get(studyName).getBrAPIObject().getTrialName(),
                    program.getKey()
            );
        } else {
            throw new IllegalStateException("Observation unit missing trial name and study name: " + unitId);
        }
        trialByOUId.put(unitId, trialByName.get(trialName));

        return trialByOUId;
    }
}
