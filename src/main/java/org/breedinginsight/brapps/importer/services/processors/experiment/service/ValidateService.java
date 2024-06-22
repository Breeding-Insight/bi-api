package org.breedinginsight.brapps.importer.services.processors.experiment.service;

public class ValidateService {
    // TODO: used by expUnit workflow
//    public void prepareDataForValidation(ImportContext importContext,
//                                         ExpUnitContext expUnitContext,
//                                         List<Column<?>> phenotypeCols) {
//        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
//            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
//            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(rowNum, new PendingImport());
//            List<PendingImportObject<BrAPIObservation>> observations = mappedImportRow.getObservations();
//            String observationHash;
//            if (hasAllReferenceUnitIds) {
//                String refOUId = importRow.getObsUnitID();
//                mappedImportRow.setTrial(pendingTrialByOUId.get(refOUId));
//                mappedImportRow.setLocation(pendingLocationByOUId.get(refOUId));
//                mappedImportRow.setStudy(pendingStudyByOUId.get(refOUId));
//                mappedImportRow.setObservationUnit(pendingObsUnitByOUId.get(refOUId));
//                mappedImportRow.setGermplasm(pendingGermplasmByOUId.get(refOUId));
//
//                // loop over phenotype column observation data for current row
//                for (Column<?> column : phenotypeCols) {
//                    observationHash = getObservationHash(
//                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName() +
//                                    pendingObsUnitByOUId.get(refOUId).getBrAPIObject().getObservationUnitName(),
//                            getVariableNameFromColumn(column),
//                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName()
//                    );
//
//                    // if value was blank won't be entry in map for this observation
//                    observations.add(observationByHash.get(observationHash));
//                }
//
//            } else {
//                mappedImportRow.setTrial(trialByNameNoScope.get(importRow.getExpTitle()));
//                mappedImportRow.setLocation(locationByName.get(importRow.getEnvLocation()));
//                mappedImportRow.setStudy(studyByNameNoScope.get(importRow.getEnv()));
//                mappedImportRow.setObservationUnit(observationUnitByNameNoScope.get(createObservationUnitKey(importRow)));
//                mappedImportRow.setGermplasm(getGidPIO(importRow));
//
//                // loop over phenotype column observation data for current row
//                for (Column<?> column : phenotypeCols) {
//
//                    // if value was blank won't be entry in map for this observation
//                    observations.add(observationByHash.get(getImportObservationHash(importRow, getVariableNameFromColumn(column))));
//                }
//            }
//
//            mappedBrAPIImport.put(rowNum, mappedImportRow);
//        }
//    }

    // TODO: used by create workflow
//    public void prepareDataForValidation(ImportContext importContext,
//                                         List<Column<?>> phenotypeCols) {
//        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
//            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
//            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(rowNum, new PendingImport());
//            List<PendingImportObject<BrAPIObservation>> observations = mappedImportRow.getObservations();
//            String observationHash;
//            if (hasAllReferenceUnitIds) {
//                String refOUId = importRow.getObsUnitID();
//                mappedImportRow.setTrial(pendingTrialByOUId.get(refOUId));
//                mappedImportRow.setLocation(pendingLocationByOUId.get(refOUId));
//                mappedImportRow.setStudy(pendingStudyByOUId.get(refOUId));
//                mappedImportRow.setObservationUnit(pendingObsUnitByOUId.get(refOUId));
//                mappedImportRow.setGermplasm(pendingGermplasmByOUId.get(refOUId));
//
//                // loop over phenotype column observation data for current row
//                for (Column<?> column : phenotypeCols) {
//                    observationHash = getObservationHash(
//                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName() +
//                                    pendingObsUnitByOUId.get(refOUId).getBrAPIObject().getObservationUnitName(),
//                            getVariableNameFromColumn(column),
//                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName()
//                    );
//
//                    // if value was blank won't be entry in map for this observation
//                    observations.add(observationByHash.get(observationHash));
//                }
//
//            } else {
//                mappedImportRow.setTrial(trialByNameNoScope.get(importRow.getExpTitle()));
//                mappedImportRow.setLocation(locationByName.get(importRow.getEnvLocation()));
//                mappedImportRow.setStudy(studyByNameNoScope.get(importRow.getEnv()));
//                mappedImportRow.setObservationUnit(observationUnitByNameNoScope.get(createObservationUnitKey(importRow)));
//                mappedImportRow.setGermplasm(getGidPIO(importRow));
//
//                // loop over phenotype column observation data for current row
//                for (Column<?> column : phenotypeCols) {
//
//                    // if value was blank won't be entry in map for this observation
//                    observations.add(observationByHash.get(getImportObservationHash(importRow, getVariableNameFromColumn(column))));
//                }
//            }
//
//            mappedBrAPIImport.put(rowNum, mappedImportRow);
//        }
//    }

    // TODO: used by expUnit workflow
//    public void validateFields(ImportContext importContext,
//                               PendingData pendingData,
//                               ExpUnitContext expUnitContext,
//                               List<Trait> referencedTraits, Program program,
//                                List<Column<?>> phenotypeCols) {
//        //fetching any existing observations for any OUs in the import
//        CaseInsensitiveMap<String, Trait> colVarMap = new CaseInsensitiveMap<>();
//        for ( Trait trait: referencedTraits) {
//            colVarMap.put(trait.getObservationVariableName(),trait);
//        }
//        Set<String> uniqueStudyAndObsUnit = new HashSet<>();
//        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
//            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
//            PendingImport mappedImportRow = mappedBrAPIImport.get(rowNum);
//            if (hasAllReferenceUnitIds) {
//                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
//            } else {
//                if (StringUtils.isNotBlank(importRow.getGid())) { // if GID is blank, don't bother to check if it is valid.
//                    validateGermplasm(importRow, validationErrors, rowNum, mappedImportRow.getGermplasm());
//                }
//                validateTestOrCheck(importRow, validationErrors, rowNum);
//                validateConditionallyRequired(validationErrors, rowNum, importRow, program, commit);
//                validateObservationUnits(validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);
//                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
//            }
//        }
//    }

    // TODO: used by create workflow
//    public void validateFields(ImportContext importContext,
//                               PendingData pendingData,
//                               List<Trait> referencedTraits, Program program,
//                               List<Column<?>> phenotypeCols) {
//        //fetching any existing observations for any OUs in the import
//        CaseInsensitiveMap<String, Trait> colVarMap = new CaseInsensitiveMap<>();
//        for ( Trait trait: referencedTraits) {
//            colVarMap.put(trait.getObservationVariableName(),trait);
//        }
//        Set<String> uniqueStudyAndObsUnit = new HashSet<>();
//        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
//            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
//            PendingImport mappedImportRow = mappedBrAPIImport.get(rowNum);
//            if (hasAllReferenceUnitIds) {
//                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
//            } else {
//                if (StringUtils.isNotBlank(importRow.getGid())) { // if GID is blank, don't bother to check if it is valid.
//                    validateGermplasm(importRow, validationErrors, rowNum, mappedImportRow.getGermplasm());
//                }
//                validateTestOrCheck(importRow, validationErrors, rowNum);
//                validateConditionallyRequired(validationErrors, rowNum, importRow, program, commit);
//                validateObservationUnits(validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);
//                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
//            }
//        }
//    }

    // TODO: used by create workflow
//    private void validateTestOrCheck(ExperimentObservation importRow, ValidationErrors validationErrors, int rowNum) {
//        String testOrCheck = importRow.getTestOrCheck();
//        if ( ! ( testOrCheck==null || testOrCheck.isBlank()
//                || "C".equalsIgnoreCase(testOrCheck) || "CHECK".equalsIgnoreCase(testOrCheck)
//                || "T".equalsIgnoreCase(testOrCheck) || "TEST".equalsIgnoreCase(testOrCheck) )
//        ){
//            addRowError(ExperimentObservation.Columns.TEST_CHECK, String.format("Invalid value (%s)", testOrCheck), validationErrors, rowNum) ;
//        }
//    }

    // TODO: used by create workflow
//    private void validateConditionallyRequired(ValidationErrors validationErrors, int rowNum, ExperimentObservation importRow, Program program, boolean commit) {
//        ImportObjectState expState = this.trialByNameNoScope.get(importRow.getExpTitle())
//                .getState();
//        ImportObjectState envState = this.studyByNameNoScope.get(importRow.getEnv()).getState();
//
//        String errorMessage = BLANK_FIELD_EXPERIMENT;
//        if (expState == ImportObjectState.EXISTING && envState == ImportObjectState.NEW) {
//            errorMessage = BLANK_FIELD_ENV;
//        } else if(expState == ImportObjectState.EXISTING && envState == ImportObjectState.EXISTING) {
//            errorMessage = BLANK_FIELD_OBS;
//        }
//
//        if(expState == ImportObjectState.NEW || envState == ImportObjectState.NEW) {
//            validateRequiredCell(importRow.getGid(), ExperimentObservation.Columns.GERMPLASM_GID, errorMessage, validationErrors, rowNum);
//            validateRequiredCell(importRow.getExpTitle(), ExperimentObservation.Columns.EXP_TITLE,errorMessage, validationErrors, rowNum);
//            validateRequiredCell(importRow.getExpUnit(), ExperimentObservation.Columns.EXP_UNIT, errorMessage, validationErrors, rowNum);
//            validateRequiredCell(importRow.getExpType(), ExperimentObservation.Columns.EXP_TYPE, errorMessage, validationErrors, rowNum);
//            validateRequiredCell(importRow.getEnv(), ExperimentObservation.Columns.ENV, errorMessage, validationErrors, rowNum);
//            if(validateRequiredCell(importRow.getEnvLocation(), ExperimentObservation.Columns.ENV_LOCATION, errorMessage, validationErrors, rowNum)) {
//                if(!Utilities.removeProgramKeyAndUnknownAdditionalData(this.studyByNameNoScope.get(importRow.getEnv()).getBrAPIObject().getLocationName(), program.getKey()).equals(importRow.getEnvLocation())) {
//                    addRowError(ExperimentObservation.Columns.ENV_LOCATION, ENV_LOCATION_MISMATCH, validationErrors, rowNum);
//                }
//            }
//            if(validateRequiredCell(importRow.getEnvYear(), ExperimentObservation.Columns.ENV_YEAR, errorMessage, validationErrors, rowNum)) {
//                String studyYear = StringUtils.defaultString( this.studyByNameNoScope.get(importRow.getEnv()).getBrAPIObject().getSeasons().get(0) );
//                String rowYear = importRow.getEnvYear();
//                if(commit) {
//                    rowYear = this.yearToSeasonDbId(importRow.getEnvYear(), program.getId());
//                }
//                if(StringUtils.isNotBlank(studyYear) && !studyYear.equals(rowYear)) {
//                    addRowError(ExperimentObservation.Columns.ENV_YEAR, ENV_YEAR_MISMATCH, validationErrors, rowNum);
//                }
//            }
//            validateRequiredCell(importRow.getExpUnitId(), ExperimentObservation.Columns.EXP_UNIT_ID, errorMessage, validationErrors, rowNum);
//            validateRequiredCell(importRow.getExpReplicateNo(), ExperimentObservation.Columns.REP_NUM, errorMessage, validationErrors, rowNum);
//            validateRequiredCell(importRow.getExpBlockNo(), ExperimentObservation.Columns.BLOCK_NUM, errorMessage, validationErrors, rowNum);
//
//            if(StringUtils.isNotBlank(importRow.getObsUnitID())) {
//                addRowError(ExperimentObservation.Columns.OBS_UNIT_ID, "ObsUnitID cannot be specified when creating a new environment", validationErrors, rowNum);
//            }
//        } else {
//            //Check if existing environment. If so, ObsUnitId must be assigned
//            validateRequiredCell(
//                    importRow.getObsUnitID(),
//                    ExperimentObservation.Columns.OBS_UNIT_ID,
//                    MISSING_OBS_UNIT_ID_ERROR,
//                    validationErrors,
//                    rowNum
//            );
//        }
//    }

    // TODO: used by create workflow
//    public void validateGeoCoordinates(ValidationErrors validationErrors, int rowNum, ExperimentObservation importRow) {
//
//        String lat = importRow.getLatitude();
//        String lon = importRow.getLongitude();
//        String elevation = importRow.getElevation();
//
//        // If any of Lat, Long, or Elevation are provided, Lat and Long must both be provided.
//        if (StringUtils.isNotBlank(lat) || StringUtils.isNotBlank(lon) || StringUtils.isNotBlank(elevation)) {
//            if (StringUtils.isBlank(lat)) {
//                addRowError(ExperimentObservation.Columns.LAT, "Latitude must be provided for complete coordinate specification", validationErrors, rowNum);
//            }
//            if (StringUtils.isBlank(lon)) {
//                addRowError(ExperimentObservation.Columns.LONG, "Longitude must be provided for complete coordinate specification", validationErrors, rowNum);
//            }
//        }
//
//        // Validate coordinate values
//        boolean latBadValue = false;
//        boolean lonBadValue = false;
//        boolean elevationBadValue = false;
//        double latDouble;
//        double lonDouble;
//        double elevationDouble;
//
//        // Only check latitude format if not blank since already had previous error
//        if (StringUtils.isNotBlank(lat)) {
//            try {
//                latDouble = Double.parseDouble(lat);
//                if (latDouble < -90 || latDouble > 90) {
//                    latBadValue = true;
//                }
//            } catch (NumberFormatException e) {
//                latBadValue = true;
//            }
//        }
//
//        // Only check longitude format if not blank since already had previous error
//        if (StringUtils.isNotBlank(lon)) {
//            try {
//                lonDouble = Double.parseDouble(lon);
//                if (lonDouble < -180 || lonDouble > 180) {
//                    lonBadValue = true;
//                }
//            } catch (NumberFormatException e) {
//                lonBadValue = true;
//            }
//        }
//
//        if (StringUtils.isNotBlank(elevation)) {
//            try {
//                elevationDouble = Double.parseDouble(elevation);
//            } catch (NumberFormatException e) {
//                elevationBadValue = true;
//            }
//        }
//
//        if (latBadValue) {
//            addRowError(ExperimentObservation.Columns.LAT, "Invalid Lat value (expected range -90 to 90)", validationErrors, rowNum);
//        }
//
//        if (lonBadValue) {
//            addRowError(ExperimentObservation.Columns.LONG, "Invalid Long value (expected range -180 to 180)", validationErrors, rowNum);
//        }
//
//        if (elevationBadValue) {
//            addRowError(ExperimentObservation.Columns.LONG, "Invalid Elevation value (numerals expected)", validationErrors, rowNum);
//        }
//
//    }
}
