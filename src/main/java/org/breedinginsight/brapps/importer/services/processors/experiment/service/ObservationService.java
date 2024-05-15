package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.ChangeLogEntry;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.ProcessorData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.utilities.Utilities;
import tech.tablesaw.columns.Column;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

public class ObservationService {
    public boolean validDateTimeValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    public boolean validDateValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }
    public String getObservationHash(String observationUnitName, String variableName, String studyName) {
        String concat = DigestUtils.sha256Hex(observationUnitName) +
                DigestUtils.sha256Hex(variableName) +
                DigestUtils.sha256Hex(StringUtils.defaultString(studyName));
        return DigestUtils.sha256Hex(concat);
    }
    public Map<String, BrAPIObservation> fetchExistingObservations(List<Trait> referencedTraits, Program program) throws ApiException {
        Set<String> ouDbIds = new HashSet<>();
        Set<String> variableDbIds = new HashSet<>();
        Map<String, String> variableNameByDbId = new HashMap<>();
        Map<String, String> ouNameByDbId = new HashMap<>();
        Map<String, String> studyNameByDbId = studyByNameNoScope.values()
                .stream()
                .filter(pio -> StringUtils.isNotBlank(pio.getBrAPIObject().getStudyDbId()))
                .map(PendingImportObject::getBrAPIObject)
                .collect(Collectors.toMap(BrAPIStudy::getStudyDbId, brAPIStudy -> Utilities.removeProgramKeyAndUnknownAdditionalData(brAPIStudy.getStudyName(), program.getKey())));

        studyNameByDbId.keySet().forEach(studyDbId -> {
            try {
                brAPIObservationUnitDAO.getObservationUnitsForStudyDbId(studyDbId, program).forEach(ou -> {
                    if(StringUtils.isNotBlank(ou.getObservationUnitDbId())) {
                        ouDbIds.add(ou.getObservationUnitDbId());
                    }
                    ouNameByDbId.put(ou.getObservationUnitDbId(), Utilities.removeProgramKeyAndUnknownAdditionalData(ou.getObservationUnitName(), program.getKey()));
                });
            } catch (ApiException e) {
                throw new RuntimeException(e);
            }
        });

        for (Trait referencedTrait : referencedTraits) {
            variableDbIds.add(referencedTrait.getObservationVariableDbId());
            variableNameByDbId.put(referencedTrait.getObservationVariableDbId(), referencedTrait.getObservationVariableName());
        }

        List<BrAPIObservation> existingObservations = brAPIObservationDAO.getObservationsByObservationUnitsAndVariables(ouDbIds, variableDbIds, program);

        return existingObservations.stream()
                .map(obs -> {
                    String studyName = studyNameByDbId.get(obs.getStudyDbId());
                    String variableName = variableNameByDbId.get(obs.getObservationVariableDbId());
                    String ouName = ouNameByDbId.get(obs.getObservationUnitDbId());

                    String key = getObservationHash(createObservationUnitKey(studyName, ouName), variableName, studyName);

                    return Map.entry(key, obs);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
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

    // TODO: used by both workflows
    private void updateObservationDependencyValues(Program program) {
        String programKey = program.getKey();

        // update the observations study DbIds, Observation Unit DbIds and Germplasm DbIds
        this.observationUnitByNameNoScope.values().stream()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(obsUnit -> updateObservationDbIds(obsUnit, programKey));

        // Update ObservationVariable DbIds
        List<Trait> traits = getTraitList(program);
        CaseInsensitiveMap<String, Trait> traitMap = new CaseInsensitiveMap<>();
        for ( Trait trait: traits) {
            traitMap.put(trait.getObservationVariableName(),trait);
        }
        for (PendingImportObject<BrAPIObservation> observation : this.observationByHash.values()) {
            String observationVariableName = observation.getBrAPIObject().getObservationVariableName();
            if (observationVariableName != null && traitMap.containsKey(observationVariableName)) {
                String observationVariableDbId = traitMap.get(observationVariableName).getObservationVariableDbId();
                observation.getBrAPIObject().setObservationVariableDbId(observationVariableDbId);
            }
        }
    }

    // TODO: used by both workflows
    public List<BrAPIObservation> commitNewPendingObservationsToBrAPIStore(ImportContext context, PendingData pendingData) {
        // filter out observations with no 'value' so they will not be saved
        List<BrAPIObservation> newObservations = ProcessorData.getNewObjects(this.observationByHash)
                .stream()
                .filter(obs -> !obs.getValue().isBlank())
                .collect(Collectors.toList());

        updateObservationDependencyValues(program);
        return brAPIObservationDAO.createBrAPIObservations(newObservations, program.getId(), upload);

    }

    // TODO: used by both workflows
    public List<BrAPIObservation> commitUpdatedPendingObservationsToBrAPIStore(ImportContext importContext, PendingData pendingData) {
        List<BrAPIObservation> updatedObservations = new ArrayList<>();
        Map<String, BrAPIObservation> mutatedObservationByDbId = ProcessorData
                .getMutationsByObjectId(observationByHash, BrAPIObservation::getObservationDbId);

        for (Map.Entry<String, BrAPIObservation> entry : mutatedObservationByDbId.entrySet()) {
            String id = entry.getKey();
            BrAPIObservation observation = entry.getValue();
            try {
                if (observation == null) {
                    throw new Exception("Null observation");
                }
                BrAPIObservation updatedObs = brAPIObservationDAO.updateBrAPIObservation(id, observation, program.getId());
                updatedObservations.add(updatedObs);

                if (updatedObs == null) {
                    throw new Exception("Null updated observation");
                }

                if (!Objects.equals(observation.getValue(), updatedObs.getValue())
                        || !Objects.equals(observation.getObservationTimeStamp(), updatedObs.getObservationTimeStamp())) {
                    String message;
                    if (!Objects.equals(observation.getValue(), updatedObs.getValue())) {
                        message = String.format("Updated observation, %s, from BrAPI service does not match requested update %s.", updatedObs.getValue(), observation.getValue());
                    } else {
                        message = String.format("Updated observation timestamp, %s, from BrAPI service does not match requested update timestamp %s.", updatedObs.getObservationTimeStamp(), observation.getObservationTimeStamp());
                    }
                    throw new Exception(message);
                }

            } catch (ApiException e) {
                log.error("Error updating observation: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException("Error saving experiment import", e);
            } catch (Exception e) {
                log.error("Error updating observation: ", e);
                throw new InternalServerException(e.getMessage(), e);
            }
        }

        return updatedObservations;
    }
}
