package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.reactivex.functions.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPISeason;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPISeasonDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.model.Program;
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
public class StudyService {

    private final BrAPISeasonDAO brAPISeasonDAO;
    private final BrAPIStudyDAO brAPIStudyDAO;

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public StudyService(BrAPISeasonDAO brAPISeasonDAO,
                        BrAPIStudyDAO brAPIStudyDAO) {
        this.brAPISeasonDAO = brAPISeasonDAO;
        this.brAPIStudyDAO = brAPIStudyDAO;
    }

    // TODO: used by both workflows
    public PendingImportObject<BrAPIStudy> processAndCacheStudy(
            BrAPIStudy existingStudy,
            Program program,
            Function<BrAPIStudy, String> getterFunction,
            Map<String, PendingImportObject<BrAPIStudy>> studyMap) throws Exception {
        PendingImportObject<BrAPIStudy> pendingStudy;
        BrAPIExternalReference xref = Utilities.getExternalReference(existingStudy.getExternalReferences(), String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.STUDIES.getName()))
                .orElseThrow(() -> new IllegalStateException("External references wasn't found for study (dbid): " + existingStudy.getStudyDbId()));
        // map season dbid to year
        String seasonDbId = existingStudy.getSeasons().get(0); // It is assumed that the study has only one season
        if(StringUtils.isNotBlank(seasonDbId)) {
            String seasonYear = seasonDbIdToYear(seasonDbId, program.getId());
            existingStudy.setSeasons(Collections.singletonList(seasonYear));
        }
        pendingStudy = new PendingImportObject<>(
                ImportObjectState.EXISTING,
                (BrAPIStudy) Utilities.formatBrapiObjForDisplay(existingStudy, BrAPIStudy.class, program),
                UUID.fromString(xref.getReferenceId())
        );
        studyMap.put(
                Utilities.removeProgramKeyAndUnknownAdditionalData(getterFunction.apply(existingStudy), program.getKey()),
                pendingStudy
        );
        return pendingStudy;
    }

    // TODO: used by both workflows
    private String seasonDbIdToYear(String seasonDbId, UUID programId) {
        String year = null;
        // TODO: add season objects to redis cache then just extract year from those
        // removing this for now here
        //if (this.seasonDbIdToYearCache.containsKey(seasonDbId)) { // get it from cache if possible
        //    year = this.seasonDbIdToYearCache.get(seasonDbId);
        //} else {
        year = seasonDbIdToYearFromDatabase(seasonDbId, programId);
        //    this.seasonDbIdToYearCache.put(seasonDbId, year);
        //}
        return year;
    }

    // TODO: used by both workflows
    private String seasonDbIdToYearFromDatabase(String seasonDbId, UUID programId) {
        BrAPISeason season = null;
        try {
            season = this.brAPISeasonDAO.getSeasonById(seasonDbId, programId);
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
        }
        Integer yearInt = (season == null) ? null : season.getYear();
        return (yearInt == null) ? "" : yearInt.toString();
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
    public List<BrAPIStudy> fetchStudiesByDbId(Set<String> studyDbIds, Program program) throws ApiException {
        List<BrAPIStudy> studies = brAPIStudyDAO.getStudiesByStudyDbId(studyDbIds, program);
        if (studies.size() != studyDbIds.size()) {
            List<String> missingIds = new ArrayList<>(studyDbIds);
            missingIds.removeAll(studies.stream().map(BrAPIStudy::getStudyDbId).collect(Collectors.toList()));
            throw new IllegalStateException(
                    "Study not found for studyDbId(s): " + String.join(ExperimentUtilities.COMMA_DELIMITER, missingIds));
        }
        return studies;
    }

    // TODO: used by both workflows
    private void initializeStudiesForExistingObservationUnits(
            Program program,
            Map<String, PendingImportObject<BrAPIStudy>> studyByName
    ) throws Exception {
        Set<String> studyDbIds = observationUnitByNameNoScope.values()
                .stream()
                .map(pio -> pio.getBrAPIObject()
                        .getStudyDbId())
                .collect(Collectors.toSet());

        List<BrAPIStudy> studies = fetchStudiesByDbId(studyDbIds, program);
        for (BrAPIStudy study : studies) {
            processAndCacheStudy(study, program, BrAPIStudy::getStudyName, studyByName);
        }
    }

    // TODO: used by expunit workflow
    public Map<String, PendingImportObject<BrAPIStudy>> mapPendingStudyByOUId(
            String unitId,
            BrAPIObservationUnit unit,
            Map<String, PendingImportObject<BrAPIStudy>> studyByName,
            Map<String, PendingImportObject<BrAPIStudy>> studyByOUId,
            Program program
    ) {
        if (unit.getStudyName() != null) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getStudyName(), program.getKey());
            studyByOUId.put(unitId, studyByName.get(studyName));
        } else {
            throw new IllegalStateException("Observation unit missing study name: " + unitId);
        }

        return studyByOUId;
    }

    // TODO: used by expunit workflow
    private PendingImportObject<BrAPIStudy> fetchOrCreateStudyPIO(
            ImportContext importContext,
            ExpUnitContext expUnitContext,
            String expSequenceValue,
            Supplier<BigInteger> envNextVal
    ) throws UnprocessableEntityException {
        PendingImportObject<BrAPIStudy> pio;
        if (hasAllReferenceUnitIds) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getStudyName(),
                    program.getKey()
            );
            pio = studyByNameNoScope.get(studyName);
            if (!commit){
                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
            }
        } else if (studyByNameNoScope.containsKey(importRow.getEnv())) {
            pio = studyByNameNoScope.get(importRow.getEnv());
            if (!commit){
                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
            }
        } else {
            PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
                    getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, commit, BRAPI_REFERENCE_SOURCE, expSequenceValue, trialID, id, envNextVal);
            newStudy.setLocationDbId(this.locationByName.get(importRow.getEnvLocation()).getId().toString()); //set as the BI ID to facilitate looking up locations when saving new studies

            // It is assumed that the study has only one season, And that the Years and not
            // the dbId's are stored in getSeason() list.
            String year = newStudy.getSeasons().get(0); // It is assumed that the study has only one season
            if (commit) {
                if(StringUtils.isNotBlank(year)) {
                    String seasonID = this.yearToSeasonDbId(year, program.getId());
                    newStudy.setSeasons(Collections.singletonList(seasonID));
                }
            } else {
                addYearToStudyAdditionalInfo(program, newStudy, year);
            }

            pio = new PendingImportObject<>(ImportObjectState.NEW, newStudy, id);
            this.studyByNameNoScope.put(importRow.getEnv(), pio);
        }
        return pio;
    }

    // TODO: used by create workflow
    private PendingImportObject<BrAPIStudy> fetchOrCreateStudyPIO(
            ImportContext importContext,
            String expSequenceValue,
            Supplier<BigInteger> envNextVal
    ) throws UnprocessableEntityException {
        PendingImportObject<BrAPIStudy> pio;
        if (hasAllReferenceUnitIds) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getStudyName(),
                    program.getKey()
            );
            pio = studyByNameNoScope.get(studyName);
            if (!commit){
                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
            }
        } else if (studyByNameNoScope.containsKey(importRow.getEnv())) {
            pio = studyByNameNoScope.get(importRow.getEnv());
            if (!commit){
                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
            }
        } else {
            PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
                    getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());
            UUID trialID = trialPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, commit, BRAPI_REFERENCE_SOURCE, expSequenceValue, trialID, id, envNextVal);
            newStudy.setLocationDbId(this.locationByName.get(importRow.getEnvLocation()).getId().toString()); //set as the BI ID to facilitate looking up locations when saving new studies

            // It is assumed that the study has only one season, And that the Years and not
            // the dbId's are stored in getSeason() list.
            String year = newStudy.getSeasons().get(0); // It is assumed that the study has only one season
            if (commit) {
                if(StringUtils.isNotBlank(year)) {
                    String seasonID = this.yearToSeasonDbId(year, program.getId());
                    newStudy.setSeasons(Collections.singletonList(seasonID));
                }
            } else {
                addYearToStudyAdditionalInfo(program, newStudy, year);
            }

            pio = new PendingImportObject<>(ImportObjectState.NEW, newStudy, id);
            this.studyByNameNoScope.put(importRow.getEnv(), pio);
        }
        return pio;
    }
}
