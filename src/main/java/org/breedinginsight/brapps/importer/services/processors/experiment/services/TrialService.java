package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    // TODO: also used in other workflow

    /**
     * Initializes trials for existing observation units.
     *
     * @param program The program object.
     * @param observationUnitByNameNoScope A map containing observation units by name (without scope).
     * @param trialByName A map containing trials by name. (will be modified in place)
     *
     */
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

    // TODO: overloaded method used by expunit workflow
    public PendingImportObject<BrAPITrial> fetchOrCreateTrialPIO(
            ImportContext importContext,
            ExpUnitContext expUnitContext
    ) throws UnprocessableEntityException {
        PendingImportObject<BrAPITrial> trialPio;



            trialPio = getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES);


        return trialPio;
    }

    // TODO: overloaded method used by create workflow
    private PendingImportObject<BrAPITrial> fetchOrCreateTrialPIO(
            ImportContext importContext
    ) throws UnprocessableEntityException {
        PendingImportObject<BrAPITrial> trialPio;


            if (trialByNameNoScope.containsKey(importRow.getExpTitle())) {
                PendingImportObject<BrAPIStudy> envPio;
                trialPio = trialByNameNoScope.get(importRow.getExpTitle());
                envPio = studyByNameNoScope.get(importRow.getEnv());

                // creating new units for existing experiments and environments is not possible
                if  (trialPio!=null &&  ImportObjectState.EXISTING==trialPio.getState() &&
                        (StringUtils.isBlank( importRow.getObsUnitID() )) && (envPio!=null && ImportObjectState.EXISTING==envPio.getState() ) ){
                    throw new UnprocessableEntityException(PREEXISTING_EXPERIMENT_TITLE);
                }
            } else if (!trialByNameNoScope.isEmpty()) {
                throw new UnprocessableEntityException(MULTIPLE_EXP_TITLES);
            } else {
                UUID id = UUID.randomUUID();
                String expSeqValue = null;
                if (commit) {
                    expSeqValue = expNextVal.get().toString();
                }
                BrAPITrial newTrial = importRow.constructBrAPITrial(program, user, commit, BRAPI_REFERENCE_SOURCE, id, expSeqValue);
                trialPio = new PendingImportObject<>(ImportObjectState.NEW, newTrial, id);
                trialByNameNoScope.put(importRow.getExpTitle(), trialPio);
            }

        return trialPio;
    }
}
