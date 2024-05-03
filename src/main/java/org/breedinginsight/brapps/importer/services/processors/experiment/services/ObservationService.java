package org.breedinginsight.brapps.importer.services.processors.experiment.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.ChangeLogEntry;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.model.Trait;
import org.breedinginsight.model.User;
import tech.tablesaw.columns.Column;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ObservationService {
    // TODO: used with expUnit workflow
    public void validateObservations(PendingData pendingData,
                                     int rowNum,
                                     ImportContext importContext,
                                     ExpUnitContext expUnitContext,
                                     List<Column<?>> phenotypeCols,
                                     CaseInsensitiveMap<String, Trait> colVarMap) {
        for (Column<?> phenoCol : phenotypeCols) {
            String importHash;
            String importObsValue = phenoCol.getString(rowNum);

            importHash = getImportObservationHash(
                    pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getObservationUnitName(),
                    getVariableNameFromColumn(phenoCol),
                    pendingStudyByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getStudyName()
            );

            validateObservation(importHash);
        }
    }

    // TODO: used with create workflow
    public void validateObservations(PendingData pendingData,
                                     int rowNum,
                                     ImportContext importContext,
                                     List<Column<?>> phenotypeCols,
                                     CaseInsensitiveMap<String, Trait> colVarMap) {


        for (Column<?> phenoCol : phenotypeCols) {
            String importHash;
            String importObsValue = phenoCol.getString(rowNum);

            importHash = getImportObservationHash(importRow, phenoCol.name());

            validateObservation(importHash);
        }

    }

    // TODO: used by both workflows
    private void validateObservation(String importHash) {


        String importObsValue = phenoCol.getString(rowNum);


        // error if import observation data already exists and user has not selected to overwrite
        if (commit && "false".equals(importRow.getOverwrite() == null ? "false" : importRow.getOverwrite()) &&
                this.existingObsByObsHash.containsKey(importHash) &&
                StringUtils.isNotBlank(phenoCol.getString(rowNum)) &&
                !this.existingObsByObsHash.get(importHash).getValue().equals(phenoCol.getString(rowNum))) {
            addRowError(
                    phenoCol.name(),
                    String.format("Value already exists for ObsUnitId: %s, Phenotype: %s", importRow.getObsUnitID(), phenoCol.name()),
                    validationErrors, rowNum
            );

            // preview case where observation has already been committed and the import row ObsVar data differs from what
            // had been saved prior to import
        } else if (existingObsByObsHash.containsKey(importHash) && !isObservationMatched(importHash, importObsValue, phenoCol, rowNum)) {

            // add a change log entry when updating the value of an observation
            if (commit) {
                BrAPIObservation pendingObservation = observationByHash.get(importHash).getBrAPIObject();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
                String timestamp = formatter.format(OffsetDateTime.now());
                String reason = importRow.getOverwriteReason() != null ? importRow.getOverwriteReason() : "";
                String prior = "";
                if (isValueMatched(importHash, importObsValue)) {
                    prior.concat(existingObsByObsHash.get(importHash).getValue());
                }
                if (timeStampColByPheno.containsKey(phenoCol.name()) && isTimestampMatched(importHash, timeStampColByPheno.get(phenoCol.name()).getString(rowNum))) {
                    prior = prior.isEmpty() ? prior : prior.concat(" ");
                    prior.concat(existingObsByObsHash.get(importHash).getObservationTimeStamp().toString());
                }
                ChangeLogEntry change = new ChangeLogEntry(prior,
                        reason,
                        user.getId(),
                        timestamp
                );

                // create the changelog field in additional info if it does not already exist
                if (pendingObservation.getAdditionalInfo().isJsonNull()) {
                    pendingObservation.setAdditionalInfo(new JsonObject());
                    pendingObservation.getAdditionalInfo().add(BrAPIAdditionalInfoFields.CHANGELOG, new JsonArray());
                }

                if (pendingObservation.getAdditionalInfo() != null && !pendingObservation.getAdditionalInfo().has(BrAPIAdditionalInfoFields.CHANGELOG)) {
                    pendingObservation.getAdditionalInfo().add(BrAPIAdditionalInfoFields.CHANGELOG, new JsonArray());
                }

                // add a new entry to the changelog
                pendingObservation.getAdditionalInfo().get(BrAPIAdditionalInfoFields.CHANGELOG).getAsJsonArray().add(gson.toJsonTree(change).getAsJsonObject());
            }

            // preview case where observation has already been committed and import ObsVar data is the
            // same as has been committed prior to import
        } else if (isObservationMatched(importHash, importObsValue, phenoCol, rowNum)) {
            BrAPIObservation existingObs = this.existingObsByObsHash.get(importHash);
            existingObs.setObservationVariableName(phenoCol.name());
            observationByHash.get(importHash).setState(ImportObjectState.EXISTING);
            observationByHash.get(importHash).setBrAPIObject(existingObs);

            // preview case where observation has already been committed and import ObsVar data is empty prior to import
        } else if (!existingObsByObsHash.containsKey(importHash) && (StringUtils.isBlank(phenoCol.getString(rowNum)))) {
            observationByHash.get(importHash).setState(ImportObjectState.EXISTING);
        } else {
            validateObservationValue(colVarMap.get(phenoCol.name()), phenoCol.getString(rowNum), phenoCol.name(), validationErrors, rowNum);

            //Timestamp validation
            if (timeStampColByPheno.containsKey(phenoCol.name())) {
                Column<?> timeStampCol = timeStampColByPheno.get(phenoCol.name());
                validateTimeStampValue(timeStampCol.getString(rowNum), timeStampCol.name(), validationErrors, rowNum);
            }
        }

    }
}
