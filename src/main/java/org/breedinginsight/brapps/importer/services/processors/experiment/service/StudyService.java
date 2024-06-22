package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import io.micronaut.context.annotation.Property;
import io.reactivex.functions.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPISeason;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPISeasonDAO;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
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
public class StudyService {
    private final Map<String, String> seasonDbIdToYearCache = new HashMap<>();
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

    /**
     * Constructs a PendingImportObject containing a BrAPIStudy object based on the provided BrAPIStudy and Program.
     * This function retrieves the external reference for the study and maps the season dbid to the corresponding year.
     *
     * @param brAPIStudy The BrAPIStudy object to construct the PendingImportObject from.
     * @param program The Program object associated with the study.
     * @return A PendingImportObject containing the formatted BrAPIStudy object.
     * @throws IllegalStateException If the external reference for the study is not found.
     */
    public PendingImportObject<BrAPIStudy> constructPIOFromBrapiStudy(BrAPIStudy brAPIStudy, Program program) {
        // Retrieve external reference for the study
        BrAPIExternalReference xref = Utilities.getExternalReference(brAPIStudy.getExternalReferences(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.STUDIES.getName()))
                .orElseThrow(() -> new IllegalStateException("External references weren't found for study (dbid): " + brAPIStudy.getStudyDbId()));

        // Map season dbid to year
        String seasonDbId = brAPIStudy.getSeasons().get(0); // It is assumed that the study has only one season
        if(StringUtils.isNotBlank(seasonDbId)) {
            String seasonYear = seasonDbIdToYear(seasonDbId, program.getId());
            brAPIStudy.setSeasons(Collections.singletonList(seasonYear));
        }

        // Create and return a PendingImportObject for the BrAPIStudy
        return new PendingImportObject<>(
                ImportObjectState.EXISTING,
                (BrAPIStudy) Utilities.formatBrapiObjForDisplay(brAPIStudy, BrAPIStudy.class, program),
                UUID.fromString(xref.getReferenceId())
        );
    }

    // TODO: used by both workflows
    public String seasonDbIdToYear(String seasonDbId, UUID programId) {
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

    public String yearToSeasonDbIdFromDatabase(String year, UUID programId) {
        BrAPISeason targetSeason = null;
        List<BrAPISeason> seasons;
        try {
            seasons = this.brAPISeasonDAO.getSeasonsByYear(year, programId);
            for (BrAPISeason season : seasons) {
                if (null == season.getSeasonName() || season.getSeasonName().isBlank() || season.getSeasonName().equals(year)) {
                    targetSeason = season;
                    break;
                }
            }
            if (targetSeason == null) {
                BrAPISeason newSeason = new BrAPISeason();
                Integer intYear = null;
                if( StringUtils.isNotBlank(year) ){
                    intYear = Integer.parseInt(year);
                }
                newSeason.setYear(intYear);
                newSeason.setSeasonName(year);
                targetSeason = this.brAPISeasonDAO.addOneSeason(newSeason, programId);
            }

        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            log.error(e.getResponseBody(), e);
        }

        return (targetSeason == null) ? null : targetSeason.getSeasonDbId();
    }

    public List<BrAPISeason> seasonsFromDatabase(String year, UUID programId) {
        List<BrAPISeason> seasons = null;
        try {
            seasons = this.brAPISeasonDAO.getSeasonsByYear(year, programId);
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
        }

        return seasons;
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

    /**
     * Fetch BrAPI studies by their database identifiers for a given program.
     *
     * This method retrieves a list of BrAPI studies based on the provided set of study database identifiers
     * and a specified program. It utilizes the BrAPIStudyDAO to fetch studies from the database.
     *
     * @param studyDbIds A set of study database identifiers for filtering the studies.
     * @param program The program related to the studies.
     * @return A list of BrAPIStudy objects representing the fetched studies.
     * @throws ApiException If there are issues in retrieving studies or if any study database identifier is missing.
     */
    public List<BrAPIStudy> fetchBrapiStudiesByDbId(Set<String> studyDbIds, Program program) throws ApiException {
        List<BrAPIStudy> brapiStudies = null; // Initializing the study object
        brapiStudies = brAPIStudyDAO.getStudiesByStudyDbId(studyDbIds, program); // Retrieving studies from the database

        // If no studies are found, throw an IllegalStateException with an error message
        if (studyDbIds.size() != brapiStudies.size()) {
            Set<String> missingIds = new HashSet<>(studyDbIds);
            missingIds.removeAll(brapiStudies.stream().map(BrAPIStudy::getStudyDbId).collect(Collectors.toSet()));
            throw new IllegalStateException("Study not found for location dbid(s): " + String.join(COMMA_DELIMITER, missingIds));
        }

        return brapiStudies;
    }

    /**
     * Retrieves the study database ID belonging to a pending unit in BrAPI format.
     *
     * This method takes a PendingImportObject containing a BrAPIObservationUnit
     * object and returns the study database ID associated with the unit, if it exists.
     *
     * @param pio The PendingImportObject containing the BrAPIObservationUnit object for which the study database ID is to be retrieved.
     * @return The study database ID belonging to the pending unit, or null if the unit does not exist or if the study database ID is not set.
     */
    public String getStudyDbIdBelongingToPendingUnit(PendingImportObject<BrAPIObservationUnit> pio) {
        String studyDbId = null;

        // Check if the BrAPI object in the PendingImportObject is not null
        if (pio.getBrAPIObject() != null) {
            // Retrieve the study database ID from the BrAPIObservationUnit object
            studyDbId = pio.getBrAPIObject().getStudyDbId();
        }

        return studyDbId;
    }

    // TODO: used by both workflows
//    private void initializeStudiesForExistingObservationUnits(
//            Program program,
//            Map<String, PendingImportObject<BrAPIStudy>> studyByName
//    ) throws Exception {
//        Set<String> studyDbIds = observationUnitByNameNoScope.values()
//                .stream()
//                .map(pio -> pio.getBrAPIObject()
//                        .getStudyDbId())
//                .collect(Collectors.toSet());
//
//        List<BrAPIStudy> studies = fetchStudiesByDbId(studyDbIds, program);
//        for (BrAPIStudy study : studies) {
//            processAndCacheStudy(study, program, BrAPIStudy::getStudyName, studyByName);
//        }
//    }

//    public Map<String, PendingImportObject<BrAPIStudy>> mapPendingStudyByOUId(
//            String unitId,
//            BrAPIObservationUnit unit,
//            Map<String, PendingImportObject<BrAPIStudy>> studyByName,
//            Map<String, PendingImportObject<BrAPIStudy>> studyByOUId,
//            Program program
//    ) {
//        if (unit.getStudyName() != null) {
//            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getStudyName(), program.getKey());
//            studyByOUId.put(unitId, studyByName.get(studyName));
//        } else {
//            throw new IllegalStateException("Observation unit missing study name: " + unitId);
//        }
//
//        return studyByOUId;
//    }

    // TODO: used by expunit workflow
//    private PendingImportObject<BrAPIStudy> fetchOrCreateStudyPIO(
//            ImportContext importContext,
//            ExpUnitContext expUnitContext,
//            String expSequenceValue,
//            Supplier<BigInteger> envNextVal
//    ) throws UnprocessableEntityException {
//        PendingImportObject<BrAPIStudy> pio;
//        if (hasAllReferenceUnitIds) {
//            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
//                    pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getStudyName(),
//                    program.getKey()
//            );
//            pio = studyByNameNoScope.get(studyName);
//            if (!commit){
//                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
//            }
//        } else if (studyByNameNoScope.containsKey(importRow.getEnv())) {
//            pio = studyByNameNoScope.get(importRow.getEnv());
//            if (!commit){
//                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
//            }
//        } else {
//            PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
//                    getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());
//            UUID trialID = trialPIO.getId();
//            UUID id = UUID.randomUUID();
//            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, commit, BRAPI_REFERENCE_SOURCE, expSequenceValue, trialID, id, envNextVal);
//            newStudy.setLocationDbId(this.locationByName.get(importRow.getEnvLocation()).getId().toString()); //set as the BI ID to facilitate looking up locations when saving new studies
//
//            // It is assumed that the study has only one season, And that the Years and not
//            // the dbId's are stored in getSeason() list.
//            String year = newStudy.getSeasons().get(0); // It is assumed that the study has only one season
//            if (commit) {
//                if(StringUtils.isNotBlank(year)) {
//                    String seasonID = this.yearToSeasonDbId(year, program.getId());
//                    newStudy.setSeasons(Collections.singletonList(seasonID));
//                }
//            } else {
//                addYearToStudyAdditionalInfo(program, newStudy, year);
//            }
//
//            pio = new PendingImportObject<>(ImportObjectState.NEW, newStudy, id);
//            this.studyByNameNoScope.put(importRow.getEnv(), pio);
//        }
//        return pio;
//    }

    // TODO: used by create workflow
//    private PendingImportObject<BrAPIStudy> fetchOrCreateStudyPIO(
//            ImportContext importContext,
//            String expSequenceValue,
//            Supplier<BigInteger> envNextVal
//    ) throws UnprocessableEntityException {
//        PendingImportObject<BrAPIStudy> pio;
//        if (hasAllReferenceUnitIds) {
//            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
//                    pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getStudyName(),
//                    program.getKey()
//            );
//            pio = studyByNameNoScope.get(studyName);
//            if (!commit){
//                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
//            }
//        } else if (studyByNameNoScope.containsKey(importRow.getEnv())) {
//            pio = studyByNameNoScope.get(importRow.getEnv());
//            if (!commit){
//                addYearToStudyAdditionalInfo(program, pio.getBrAPIObject());
//            }
//        } else {
//            PendingImportObject<BrAPITrial> trialPIO = hasAllReferenceUnitIds ?
//                    getSingleEntryValue(trialByNameNoScope, MULTIPLE_EXP_TITLES) : trialByNameNoScope.get(importRow.getExpTitle());
//            UUID trialID = trialPIO.getId();
//            UUID id = UUID.randomUUID();
//            BrAPIStudy newStudy = importRow.constructBrAPIStudy(program, commit, BRAPI_REFERENCE_SOURCE, expSequenceValue, trialID, id, envNextVal);
//            newStudy.setLocationDbId(this.locationByName.get(importRow.getEnvLocation()).getId().toString()); //set as the BI ID to facilitate looking up locations when saving new studies
//
//            // It is assumed that the study has only one season, And that the Years and not
//            // the dbId's are stored in getSeason() list.
//            String year = newStudy.getSeasons().get(0); // It is assumed that the study has only one season
//            if (commit) {
//                if(StringUtils.isNotBlank(year)) {
//                    String seasonID = this.yearToSeasonDbId(year, program.getId());
//                    newStudy.setSeasons(Collections.singletonList(seasonID));
//                }
//            } else {
//                addYearToStudyAdditionalInfo(program, newStudy, year);
//            }
//
//            pio = new PendingImportObject<>(ImportObjectState.NEW, newStudy, id);
//            this.studyByNameNoScope.put(importRow.getEnv(), pio);
//        }
//        return pio;
//    }

//    private void updateStudyDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport, String programKey) {
//        // update location DbIds in studies for all distinct locations
//        mappedBrAPIImport.values()
//                .stream()
//                .map(PendingImport::getLocation)
//                .forEach(this::updateStudyLocationDbId);
//
//        // update trial DbIds in studies for all distinct trials
//        this.trialByNameNoScope.values()
//                .stream()
//                .filter(Objects::nonNull)
//                .distinct()
//                .map(PendingImportObject::getBrAPIObject)
//                .forEach(trial -> this.updateTrialDbId(trial, programKey));
//    }
//
//    private void updateStudyLocationDbId(PendingImportObject<ProgramLocation> location) {
//        this.studyByNameNoScope.values()
//                .stream()
//                .filter(study -> location.getId().toString()
//                        .equals(study.getBrAPIObject()
//                                .getLocationDbId()))
//                .forEach(study -> study.getBrAPIObject()
//                        .setLocationDbId(location.getBrAPIObject().getLocationDbId()));
//    }
//
//    private void updateTrialDbId(BrAPITrial trial, String programKey) {
//        this.studyByNameNoScope.values()
//                .stream()
//                .filter(study -> study.getBrAPIObject()
//                        .getTrialName()
//                        .equals(Utilities.removeProgramKey(trial.getTrialName(), programKey)))
//                .forEach(study -> study.getBrAPIObject()
//                        .setTrialDbId(trial.getTrialDbId()));
//    }

    // TODO: used by both workflows
//    public List<BrAPIStudy> commitNewPendingStudiessToBrAPIStore(ImportContext context, PendingData pendingData) {
//        List<BrAPIStudy> newStudies = ProcessorData.getNewObjects(this.studyByNameNoScope);
//        updateStudyDependencyValues(mappedBrAPIImport, program.getKey());
//        List<BrAPIStudy> createdStudies = brAPIStudyDAO.createBrAPIStudies(newStudies, program.getId(), upload);
//
//        // set the DbId to the for each newly created study
//        for (BrAPIStudy createdStudy : createdStudies) {
//            String createdStudy_name_no_key = Utilities.removeProgramKeyAndUnknownAdditionalData(createdStudy.getStudyName(), program.getKey());
//            this.studyByNameNoScope.get(createdStudy_name_no_key)
//                    .getBrAPIObject()
//                    .setStudyDbId(createdStudy.getStudyDbId());
//        }
//
//        return createdStudies;
//    }

    // TODO: used by both workflows
    public List<BrAPIStudy> commitUpdatedPendingStudiesToBrAPIStore(ImportContext importContext, PendingData pendingData) {
        List<BrAPIStudy> updatedStudies = new ArrayList<>();

        return updatedStudies;
    }
}
