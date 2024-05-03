package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import tech.tablesaw.columns.Column;

import java.util.List;
import java.util.Map;

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
}
