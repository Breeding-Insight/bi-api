package org.breedinginsight.brapps.importer.services.processors.experiment.create.steps;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.pipeline.ProcessingStep;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Prototype
@Slf4j
public class GetExistingProcessingStep implements ProcessingStep<ImportContext, PendingData>  {

    private final BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    private final BrAPITrialDAO brAPITrialDAO;
    private final BrAPIStudyDAO brAPIStudyDAO;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public GetExistingProcessingStep(BrAPIObservationUnitDAO brAPIObservationUnitDAO,
                                     BrAPITrialDAO brAPITrialDAO,
                                     BrAPIStudyDAO brAPIStudyDAO) {
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
        this.brAPITrialDAO = brAPITrialDAO;
        this.brAPIStudyDAO = brAPIStudyDAO;
    }

    @Override
    public PendingData process(ImportContext input) {

        List<ExperimentObservation> experimentImportRows = ExperimentUtilities.importRowsToExperimentObservations(input.getImportRows());
        Program program = input.getProgram();

        // Populate pending objects with existing status
        Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope = initializeObservationUnits(program, experimentImportRows);
        Map<String, PendingImportObject<BrAPITrial>> trialByNameNoScope = initializeTrialByNameNoScope(program, observationUnitByNameNoScope, experimentImportRows);

        // TODO: populate rest of data

        PendingData existing = PendingData.builder()
                .observationUnitByNameNoScope(observationUnitByNameNoScope)
                .trialByNameNoScope(trialByNameNoScope)
                .build();

        return existing;
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
        observationUnitByName.put(createObservationUnitKey(row),
                new PendingImportObject<>(ImportObjectState.EXISTING,
                        brAPIObservationUnit,
                        UUID.fromString(idRef.getReferenceId())));
    }

    private String createObservationUnitKey(ExperimentObservation importRow) {
        return createObservationUnitKey(importRow.getEnv(), importRow.getExpUnitId());
    }

    private String createObservationUnitKey(String studyName, String obsUnitName) {
        return studyName + obsUnitName;
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

    // TODO: also used in other workflow

    /**
     * Initializes trials for existing observation units.
     *
     * @param program The program object.
     * @param observationUnitByNameNoScope A map containing observation units by name (without scope).
     * @param trialByName A map containing trials by name. (will be modified in place)
     *
     */
    private void initializeTrialsForExistingObservationUnits(Program program,
                                                             Map<String, PendingImportObject<BrAPIObservationUnit>> observationUnitByNameNoScope,
                                                             Map<String, PendingImportObject<BrAPITrial>> trialByName) {
        if(observationUnitByNameNoScope.size() > 0) {
            Set<String> trialDbIds = new HashSet<>();
            Set<String> studyDbIds = new HashSet<>();

            observationUnitByNameNoScope.values()
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
                    trialDbIds.addAll(fetchTrialDbidsForStudies(studyDbIds, program));
                } catch (ApiException e) {
                    log.error("Error fetching studies: " + Utilities.generateApiExceptionLogMessage(e), e);
                    throw new InternalServerException(e.toString(), e);
                }
            }

            try {
                List<BrAPITrial> trials = brAPITrialDAO.getTrialsByDbIds(trialDbIds, program);
                if (trials.size() != trialDbIds.size()) {
                    List<String> missingIds = new ArrayList<>(trialDbIds);
                    missingIds.removeAll(trials.stream().map(BrAPITrial::getTrialDbId).collect(Collectors.toList()));
                    throw new IllegalStateException("Trial not found for trialDbId(s): " + String.join(ExperimentUtilities.COMMA_DELIMITER, missingIds));
                }

                trials.forEach(trial -> processAndCacheTrial(trial, program, trialByName));
            } catch (ApiException e) {
                log.error("Error fetching trials: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }
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
        List<BrAPIStudy> studies = fetchStudiesByDbId(studyDbIds, program);
        studies.forEach(study -> {
            if (StringUtils.isBlank(study.getTrialDbId())) {
                throw new IllegalStateException("TrialDbId is not set for an existing Study: " + study.getStudyDbId());
            }
            trialDbIds.add(study.getTrialDbId());
        });

        return trialDbIds;
    }

    /**
     * Fetches a list of BrAPI studies by their study database IDs for a given program.
     *
     * This method queries the BrAPIStudyDAO to retrieve studies based on the provided study database IDs and the program.
     * It ensures that all requested study database IDs are found in the result set, throwing an IllegalStateException if any are missing.
     *
     * @param studyDbIds a Set of Strings representing the study database IDs to fetch
     * @param program the Program object representing the program context in which to fetch studies
     * @return a List of BrAPIStudy objects matching the provided study database IDs
     *
     * @throws ApiException if there is an issue fetching the studies
     * @throws IllegalStateException if any requested study database IDs are not found in the result set
     */
    private List<BrAPIStudy> fetchStudiesByDbId(Set<String> studyDbIds, Program program) throws ApiException {
        List<BrAPIStudy> studies = brAPIStudyDAO.getStudiesByStudyDbId(studyDbIds, program);
        if (studies.size() != studyDbIds.size()) {
            List<String> missingIds = new ArrayList<>(studyDbIds);
            missingIds.removeAll(studies.stream().map(BrAPIStudy::getStudyDbId).collect(Collectors.toList()));
            throw new IllegalStateException(
                    "Study not found for studyDbId(s): " + String.join(ExperimentUtilities.COMMA_DELIMITER, missingIds));
        }
        return studies;
    }




}
