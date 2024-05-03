package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.model.User;
import tech.tablesaw.columns.Column;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ValidateService {
    // TODO: used by expUnit workflow
    public void prepareDataForValidation(ImportContext importContext,
                                         ExpUnitContext expUnitContext,
                                         List<Column<?>> phenotypeCols) {
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(rowNum, new PendingImport());
            List<PendingImportObject<BrAPIObservation>> observations = mappedImportRow.getObservations();
            String observationHash;
            if (hasAllReferenceUnitIds) {
                String refOUId = importRow.getObsUnitID();
                mappedImportRow.setTrial(pendingTrialByOUId.get(refOUId));
                mappedImportRow.setLocation(pendingLocationByOUId.get(refOUId));
                mappedImportRow.setStudy(pendingStudyByOUId.get(refOUId));
                mappedImportRow.setObservationUnit(pendingObsUnitByOUId.get(refOUId));
                mappedImportRow.setGermplasm(pendingGermplasmByOUId.get(refOUId));

                // loop over phenotype column observation data for current row
                for (Column<?> column : phenotypeCols) {
                    observationHash = getObservationHash(
                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName() +
                                    pendingObsUnitByOUId.get(refOUId).getBrAPIObject().getObservationUnitName(),
                            getVariableNameFromColumn(column),
                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName()
                    );

                    // if value was blank won't be entry in map for this observation
                    observations.add(observationByHash.get(observationHash));
                }

            } else {
                mappedImportRow.setTrial(trialByNameNoScope.get(importRow.getExpTitle()));
                mappedImportRow.setLocation(locationByName.get(importRow.getEnvLocation()));
                mappedImportRow.setStudy(studyByNameNoScope.get(importRow.getEnv()));
                mappedImportRow.setObservationUnit(observationUnitByNameNoScope.get(createObservationUnitKey(importRow)));
                mappedImportRow.setGermplasm(getGidPIO(importRow));

                // loop over phenotype column observation data for current row
                for (Column<?> column : phenotypeCols) {

                    // if value was blank won't be entry in map for this observation
                    observations.add(observationByHash.get(getImportObservationHash(importRow, getVariableNameFromColumn(column))));
                }
            }

            mappedBrAPIImport.put(rowNum, mappedImportRow);
        }
    }

    // TODO: used by create workflow
    public void prepareDataForValidation(ImportContext importContext,
                                         List<Column<?>> phenotypeCols) {
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(rowNum, new PendingImport());
            List<PendingImportObject<BrAPIObservation>> observations = mappedImportRow.getObservations();
            String observationHash;
            if (hasAllReferenceUnitIds) {
                String refOUId = importRow.getObsUnitID();
                mappedImportRow.setTrial(pendingTrialByOUId.get(refOUId));
                mappedImportRow.setLocation(pendingLocationByOUId.get(refOUId));
                mappedImportRow.setStudy(pendingStudyByOUId.get(refOUId));
                mappedImportRow.setObservationUnit(pendingObsUnitByOUId.get(refOUId));
                mappedImportRow.setGermplasm(pendingGermplasmByOUId.get(refOUId));

                // loop over phenotype column observation data for current row
                for (Column<?> column : phenotypeCols) {
                    observationHash = getObservationHash(
                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName() +
                                    pendingObsUnitByOUId.get(refOUId).getBrAPIObject().getObservationUnitName(),
                            getVariableNameFromColumn(column),
                            pendingStudyByOUId.get(refOUId).getBrAPIObject().getStudyName()
                    );

                    // if value was blank won't be entry in map for this observation
                    observations.add(observationByHash.get(observationHash));
                }

            } else {
                mappedImportRow.setTrial(trialByNameNoScope.get(importRow.getExpTitle()));
                mappedImportRow.setLocation(locationByName.get(importRow.getEnvLocation()));
                mappedImportRow.setStudy(studyByNameNoScope.get(importRow.getEnv()));
                mappedImportRow.setObservationUnit(observationUnitByNameNoScope.get(createObservationUnitKey(importRow)));
                mappedImportRow.setGermplasm(getGidPIO(importRow));

                // loop over phenotype column observation data for current row
                for (Column<?> column : phenotypeCols) {

                    // if value was blank won't be entry in map for this observation
                    observations.add(observationByHash.get(getImportObservationHash(importRow, getVariableNameFromColumn(column))));
                }
            }

            mappedBrAPIImport.put(rowNum, mappedImportRow);
        }
    }

    // TODO: used by expUnit workflow
    public void validateFields(ImportContext importContext,
                               PendingData pendingData,
                               ExpUnitContext expUnitContext,
                               List<Trait> referencedTraits, Program program,
                                List<Column<?>> phenotypeCols) {
        //fetching any existing observations for any OUs in the import
        CaseInsensitiveMap<String, Trait> colVarMap = new CaseInsensitiveMap<>();
        for ( Trait trait: referencedTraits) {
            colVarMap.put(trait.getObservationVariableName(),trait);
        }
        Set<String> uniqueStudyAndObsUnit = new HashSet<>();
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
            PendingImport mappedImportRow = mappedBrAPIImport.get(rowNum);
            if (hasAllReferenceUnitIds) {
                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
            } else {
                if (StringUtils.isNotBlank(importRow.getGid())) { // if GID is blank, don't bother to check if it is valid.
                    validateGermplasm(importRow, validationErrors, rowNum, mappedImportRow.getGermplasm());
                }
                validateTestOrCheck(importRow, validationErrors, rowNum);
                validateConditionallyRequired(validationErrors, rowNum, importRow, program, commit);
                validateObservationUnits(validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);
                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
            }
        }
    }

    // TODO: used by create workflow
    public void validateFields(ImportContext importContext,
                               PendingData pendingData,
                               List<Trait> referencedTraits, Program program,
                               List<Column<?>> phenotypeCols) {
        //fetching any existing observations for any OUs in the import
        CaseInsensitiveMap<String, Trait> colVarMap = new CaseInsensitiveMap<>();
        for ( Trait trait: referencedTraits) {
            colVarMap.put(trait.getObservationVariableName(),trait);
        }
        Set<String> uniqueStudyAndObsUnit = new HashSet<>();
        for (int rowNum = 0; rowNum < importRows.size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) importRows.get(rowNum);
            PendingImport mappedImportRow = mappedBrAPIImport.get(rowNum);
            if (hasAllReferenceUnitIds) {
                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
            } else {
                if (StringUtils.isNotBlank(importRow.getGid())) { // if GID is blank, don't bother to check if it is valid.
                    validateGermplasm(importRow, validationErrors, rowNum, mappedImportRow.getGermplasm());
                }
                validateTestOrCheck(importRow, validationErrors, rowNum);
                validateConditionallyRequired(validationErrors, rowNum, importRow, program, commit);
                validateObservationUnits(validationErrors, uniqueStudyAndObsUnit, rowNum, importRow);
                validateObservations(validationErrors, rowNum, importRow, phenotypeCols, colVarMap, commit, user);
            }
        }
    }
}
