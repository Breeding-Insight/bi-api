package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ObservationUnitService {
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
}