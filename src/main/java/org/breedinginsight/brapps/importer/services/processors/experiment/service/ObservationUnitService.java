package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import io.micronaut.context.annotation.Property;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.ProcessorData;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants.COMMA_DELIMITER;

public class ObservationUnitService {
    private final BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public ObservationUnitService(BrAPIObservationUnitDAO brAPIObservationUnitDAO) {
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
    }

    /**
     * Retrieves a list of BrAPI (Breeding API) observation units by their database IDs for a given set of experimental unit IDs and program.
     *
     * This method queries the BrAPIObservationUnitDAO to retrieve BrAPI observation units based on the provided experimental unit IDs and program.
     * If the database IDs of the retrieved BrAPI observation units do not match the provided experimental unit IDs, an IllegalStateException is thrown.
     * The exception includes information on the missing observation unit database IDs.
     *
     * @param expUnitIds a set of experimental unit IDs for which to retrieve BrAPI observation units
     * @param program the program for which to retrieve BrAPI observation units
     * @return a list of BrAPIObservationUnit objects corresponding to the provided experimental unit IDs
     * @throws ApiException if an error occurs during the retrieval of observation units
     * @throws IllegalStateException if the retrieved observation units do not match the provided experimental unit IDs
     */
    public List<BrAPIObservationUnit> getObservationUnitsByDbId(Set<String> expUnitIds, Program program) throws ApiException, IllegalStateException {
        List<BrAPIObservationUnit> brapiUnits = null;

        // Retrieve reference Observation Units based on IDs
        brapiUnits = brAPIObservationUnitDAO.getObservationUnitsById(expUnitIds, program);

        // If no BrAPI units are found, throw an IllegalStateException with an error message
        if (expUnitIds.size() != brapiUnits.size()) {
            Set<String> missingIds = new HashSet<>(expUnitIds);

            // Calculate missing IDs based on retrieved BrAPI units
            missingIds.removeAll(brapiUnits.stream().map(BrAPIObservationUnit::getObservationUnitDbId).collect(Collectors.toSet()));

            // Throw exception with missing IDs information
            throw new IllegalStateException("Observation unit not found for unit dbid(s): " + String.join(COMMA_DELIMITER, missingIds));
        }

        return brapiUnits;
    }

    /**
     * Constructs a PendingImportObject of type BrAPIObservationUnit from a given BrAPIObservationUnit object.
     * This function is responsible for constructing an import object that represents an observation unit for the Deltabreed system,
     * using a provided BrAPIObservationUnit object from a BrAPI source.
     *
     * @param unit the BrAPIObservationUnit object to be used for constructing the PendingImportObject
     * @return a PendingImportObject of type BrAPIObservationUnit representing the imported observation unit
     * @throws IllegalStateException if the external reference for the observation unit does not exist
     */
    public PendingImportObject<BrAPIObservationUnit> constructPIOFromBrapiUnit(BrAPIObservationUnit unit) {
        final PendingImportObject<BrAPIObservationUnit>[] pio = new PendingImportObject[]{null};

        // Construct the DeltaBreed observation unit source for external references
        String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());

        // Get external reference for the Observation Unit
        Optional<BrAPIExternalReference> unitXref = Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource);
        unitXref.ifPresentOrElse(
                xref -> {
                    pio[0] = new PendingImportObject<BrAPIObservationUnit>(ImportObjectState.EXISTING, unit, UUID.fromString(xref.getReferenceId()));
                },
                () -> {

                    // but throw an error if no unit ID
                    throw new IllegalStateException("External reference does not exist for Deltabreed ObservationUnit ID");
                }
        );
        return pio[0];
    }

    /**
     * Maps pending observation units by their reference IDs.
     * This function takes a list of pending import objects representing BrAPI observation units
     * and constructs a map where the key is the external reference ID of the observation unit
     * and the value is the pending import object itself.
     *
     * @param pios List of pending import objects for BrAPI observation units
     * @return A map of pending observation units keyed by their external reference ID
     */
    public Map<String, PendingImportObject<BrAPIObservationUnit>> mapPendingUnitById(List<PendingImportObject<BrAPIObservationUnit>> pios) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById = new HashMap<>();

        // Construct the DeltaBreed observation unit source for external references
        String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());

        for (PendingImportObject<BrAPIObservationUnit> pio : pios) {

            // Get external reference for the Observation Unit
            Optional<BrAPIExternalReference> xref = Utilities.getExternalReference(pio.getBrAPIObject().getExternalReferences(), deltaBreedOUSource);
            pendingUnitById.put(xref.get().getReferenceId(),pio);
        }

        return pendingUnitById;
    }

    /**
     * This method takes a list of PendingImportObject<BrAPIObservationUnit> objects and a Program object as input
     * and maps the PendingImportObject<BrAPIObservationUnit> objects by their observation unit name without the program scope.
     *
     * @param pios A list of PendingImportObject<BrAPIObservationUnit> objects to be processed
     * @param program The Program object representing the scope to be removed from observation unit names
     * @return A Map<String, PendingImportObject<BrAPIObservationUnit>> mapping observation unit names without the program scope to the corresponding PendingImportObject<BrAPIObservationUnit> objects
     */
    public Map<String, PendingImportObject<BrAPIObservationUnit>> mapPendingUnitByNameNoScope(List<PendingImportObject<BrAPIObservationUnit>> pios,
                                                                                              Program program) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByNameNoScope = new HashMap<>();

        for (PendingImportObject<BrAPIObservationUnit> pio : pios) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    pio.getBrAPIObject().getStudyName(),
                    program.getKey()
            );
            String observationUnitName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    pio.getBrAPIObject().getObservationUnitName(),
                    program.getKey()
            );
            pendingUnitByNameNoScope.put(ExperimentUtilities.createObservationUnitKey(studyName, observationUnitName), pio);
        }

        return pendingUnitByNameNoScope;
    }

    /**
     * Collects missing Observation Unit IDs from a set of reference IDs and a list of existing Observation Units.
     *
     * This function takes a Set of reference IDs and a List of existing Observation Units, filters out the Observation Units
     * that have external references matching a specific source, and returns a List of missing Observation Unit IDs that are
     * present in the reference IDs but not found in the existing Observation Units.
     *
     * @param referenceIds The Set of reference IDs representing all possible Observation Unit IDs to match against.
     * @param existingUnits The List of existing Observation Units to compare against the reference IDs.
     * @return A List of Observation Unit IDs that are missing from the existing Observation Units but present in the reference IDs.
     */
    public List<String> collectMissingOUIds(Set<String> referenceIds, List<BrAPIObservationUnit> existingUnits) {
        List<String> missingIds = new ArrayList<>(referenceIds);

        // Construct the DeltaBreed observation unit source for external references
        String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());

        Set<String> fetchedIds = existingUnits.stream()
                .filter(unit ->Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource).isPresent())
                .map(unit->Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource).get().getReferenceId())
                .collect(Collectors.toSet());
        missingIds.removeAll(fetchedIds);

        return missingIds;
    }

    // TODO: used by expUnit workflow
    public PendingImportObject<BrAPIObservationUnit> fetchOrCreateObsUnitPIO(ImportContext importContext,
                                                                             PendingData pendingData,
                                                                             ExpUnitContext expUnitContext,
                                                                             String envSeqValue) throws ApiException, MissingRequiredInfoException, UnprocessableEntityException {
        PendingImportObject<BrAPIObservationUnit> pio;
        String key = createObservationUnitKey(importRow);
        if (hasAllReferenceUnitIds) {
            pio = pendingObsUnitByOUId.get(importRow.getObsUnitID());
        } else if (observationUnitByNameNoScope.containsKey(key)) {
            pio = observationUnitByNameNoScope.get(key);
        } else {
            String germplasmName = "";
            if (this.existingGermplasmByGID.get(importRow.getGid()) != null) {
                germplasmName = this.existingGermplasmByGID.get(importRow.getGid())
                        .getBrAPIObject()
                        .getGermplasmName();
            }
            PendingImportObject<BrAPITrial> trialPIO = trialByNameNoScope.get(importRow.getExpTitle());;
            UUID trialID = trialPIO.getId();
            UUID datasetId = null;
            if (commit) {
                datasetId = UUID.fromString(trialPIO.getBrAPIObject()
                        .getAdditionalInfo().getAsJsonObject()
                        .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString());
            }
            PendingImportObject<BrAPIStudy> studyPIO = this.studyByNameNoScope.get(importRow.getEnv());
            UUID studyID = studyPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIObservationUnit newObservationUnit = importRow.constructBrAPIObservationUnit(program, envSeqValue, commit, germplasmName, importRow.getGid(), BRAPI_REFERENCE_SOURCE, trialID, datasetId, studyID, id);

            // check for existing units if this is an existing study
            if (studyPIO.getBrAPIObject().getStudyDbId() != null) {
                List<BrAPIObservationUnit> existingOUs = brAPIObservationUnitDAO.getObservationUnitsForStudyDbId(studyPIO.getBrAPIObject().getStudyDbId(), program);
                List<BrAPIObservationUnit> matchingOU = existingOUs.stream().filter(ou -> importRow.getExpUnitId().equals(Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()))).collect(Collectors.toList());
                if (matchingOU.isEmpty()) {
                    throw new MissingRequiredInfoException(MISSING_OBS_UNIT_ID_ERROR);
                } else {
                    pio = new PendingImportObject<>(ImportObjectState.EXISTING, (BrAPIObservationUnit) Utilities.formatBrapiObjForDisplay(matchingOU.get(0), BrAPIObservationUnit.class, program));
                }
            } else {
                pio = new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit, id);
            }
            this.observationUnitByNameNoScope.put(key, pio);
        }
        return pio;
    }

    // TODO: used by create workflow
    public PendingImportObject<BrAPIObservationUnit> fetchOrCreateObsUnitPIO(ImportContext importContext,
                                                                             PendingData pendingData,
                                                                             String envSeqValue) throws ApiException, MissingRequiredInfoException, UnprocessableEntityException {
        PendingImportObject<BrAPIObservationUnit> pio;
        String key = createObservationUnitKey(importRow);
        if (hasAllReferenceUnitIds) {
            pio = pendingObsUnitByOUId.get(importRow.getObsUnitID());
        } else if (observationUnitByNameNoScope.containsKey(key)) {
            pio = observationUnitByNameNoScope.get(key);
        } else {
            String germplasmName = "";
            if (this.existingGermplasmByGID.get(importRow.getGid()) != null) {
                germplasmName = this.existingGermplasmByGID.get(importRow.getGid())
                        .getBrAPIObject()
                        .getGermplasmName();
            }
            PendingImportObject<BrAPITrial> trialPIO = trialByNameNoScope.get(importRow.getExpTitle());;
            UUID trialID = trialPIO.getId();
            UUID datasetId = null;
            if (commit) {
                datasetId = UUID.fromString(trialPIO.getBrAPIObject()
                        .getAdditionalInfo().getAsJsonObject()
                        .get(BrAPIAdditionalInfoFields.OBSERVATION_DATASET_ID).getAsString());
            }
            PendingImportObject<BrAPIStudy> studyPIO = this.studyByNameNoScope.get(importRow.getEnv());
            UUID studyID = studyPIO.getId();
            UUID id = UUID.randomUUID();
            BrAPIObservationUnit newObservationUnit = importRow.constructBrAPIObservationUnit(program, envSeqValue, commit, germplasmName, importRow.getGid(), BRAPI_REFERENCE_SOURCE, trialID, datasetId, studyID, id);

            // check for existing units if this is an existing study
            if (studyPIO.getBrAPIObject().getStudyDbId() != null) {
                List<BrAPIObservationUnit> existingOUs = brAPIObservationUnitDAO.getObservationUnitsForStudyDbId(studyPIO.getBrAPIObject().getStudyDbId(), program);
                List<BrAPIObservationUnit> matchingOU = existingOUs.stream().filter(ou -> importRow.getExpUnitId().equals(Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()))).collect(Collectors.toList());
                if (matchingOU.isEmpty()) {
                    throw new MissingRequiredInfoException(MISSING_OBS_UNIT_ID_ERROR);
                } else {
                    pio = new PendingImportObject<>(ImportObjectState.EXISTING, (BrAPIObservationUnit) Utilities.formatBrapiObjForDisplay(matchingOU.get(0), BrAPIObservationUnit.class, program));
                }
            } else {
                pio = new PendingImportObject<>(ImportObjectState.NEW, newObservationUnit, id);
            }
            this.observationUnitByNameNoScope.put(key, pio);
        }
        return pio;
    }

    // TODO: used by both workflows
    public String createObservationUnitKey(ExperimentObservation importRow) {
        return createObservationUnitKey(importRow.getEnv(), importRow.getExpUnitId());
    }

    // TODO: used by both workflows
    public String createObservationUnitKey(String studyName, String obsUnitName) {
        return studyName + obsUnitName;
    }

    // TODO: used by create workflow
    public void validateObservationUnits(ValidationErrors validationErrors,
                                          Set<String> uniqueStudyAndObsUnit,
                                          int rowNum,
                                          ExperimentObservation importRow) {
        validateUniqueObsUnits(validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);

        String key = createObservationUnitKey(importRow);
        PendingImportObject<BrAPIObservationUnit> ouPIO = observationUnitByNameNoScope.get(key);
        if(ouPIO.getState() == ImportObjectState.NEW && StringUtils.isNotBlank(importRow.getObsUnitID())) {
            addRowError(ExperimentObservation.Columns.OBS_UNIT_ID, "Could not find observation unit by ObsUnitDBID", validationErrors, rowNum);
        }

        validateGeoCoordinates(validationErrors, rowNum, importRow);
    }

    // TODO: used by both workflows
    private void updateObsUnitDependencyValues(String programKey) {

        // update study DbIds
        this.studyByNameNoScope.values()
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(study -> updateStudyDbId(study, programKey));

        // update germplasm DbIds
        this.existingGermplasmByGID.values()
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(this::updateGermplasmDbId);
    }

    // TODO: used by both workflows
    public List<BrAPIObservationUnit> commitNewPendingObservationUnitsToBrAPIStore(ImportContext context, PendingData pendingData) {
        List<BrAPIObservationUnit> newObservationUnits = ProcessorData.getNewObjects(this.observationUnitByNameNoScope);
        updateObsUnitDependencyValues(program.getKey());
        List<BrAPIObservationUnit> createdObservationUnits = brAPIObservationUnitDAO.createBrAPIObservationUnits(newObservationUnits, program.getId(), upload);

        // set the DbId to the for each newly created Observation Unit
        for (BrAPIObservationUnit createdObservationUnit : createdObservationUnits) {
            // retrieve the BrAPI ObservationUnit from this.observationUnitByNameNoScope
            String createdObservationUnit_StripedStudyName = Utilities.removeProgramKeyAndUnknownAdditionalData(createdObservationUnit.getStudyName(), program.getKey());
            String createdObservationUnit_StripedObsUnitName = Utilities.removeProgramKeyAndUnknownAdditionalData(createdObservationUnit.getObservationUnitName(), program.getKey());
            String createdObsUnit_key = createObservationUnitKey(createdObservationUnit_StripedStudyName, createdObservationUnit_StripedObsUnitName);
            this.observationUnitByNameNoScope.get(createdObsUnit_key)
                    .getBrAPIObject()
                    .setObservationUnitDbId(createdObservationUnit.getObservationUnitDbId());
        }

        return createdObservationUnits;
    }

    // TODO: used by both workflows
    public List<BrAPIObservationUnit> commitUpdatedPendingObservationUnitToBrAPIStore(ImportContext importContext, PendingData pendingData) {
        List<BrAPIObservationUnit> updatedUnits = new ArrayList<>();

        return updatedUnits;
    }

    private void updateStudyDbId(BrAPIStudy study, String programKey) {
        this.observationUnitByNameNoScope.values()
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

    private void updateGermplasmDbId(BrAPIGermplasm germplasm) {
        this.observationUnitByNameNoScope.values()
                .stream()
                .filter(obsUnit -> germplasm.getAccessionNumber() != null &&
                        germplasm.getAccessionNumber().equals(obsUnit
                                .getBrAPIObject()
                                .getAdditionalInfo().getAsJsonObject()
                                .get(BrAPIAdditionalInfoFields.GID).getAsString()))
                .forEach(obsUnit -> obsUnit.getBrAPIObject()
                        .setGermplasmDbId(germplasm.getGermplasmDbId()));
    }
}
