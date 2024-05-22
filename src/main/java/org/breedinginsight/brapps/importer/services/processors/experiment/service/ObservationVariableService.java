package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.ChangeLogEntry;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.ProcessorData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.process.OverwrittenData;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.OntologyService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import tech.tablesaw.columns.Column;
import org.breedinginsight.dao.db.tables.pojos.TraitEntity;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ObservationVariableService {
    private final OntologyService ontologyService;

    @Inject
    public ObservationVariableService(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    /**
     * Fetches traits by name for the given set of variable names and program.
     *
     * This method fetches all stored traits for the specified program and filters them based on the set of variable names provided.
     * It ensures that all requested observation variables are present and returns a list of matching traits.
     * If any observation variables are missing, it throws an IllegalStateException with the missing variable names.
     *
     * @param varNames a set of variable names to fetch traits for
     * @param program the program for which traits are fetched
     * @return a list of traits filtered by the provided variable names
     * @throws DoesNotExistException if the program or traits do not exist
     * @throws IllegalStateException if any requested observation variables are missing
     */
    public List<Trait> fetchTraitsByName(Set<String> varNames, Program program) throws DoesNotExistException, IllegalStateException {
        List<Trait> traits = null;

        // Fetch all stored traits for the program
        List<Trait> programTraits = ontologyService.getTraitsByProgramId(program.getId(), true);

        // Only keep traits that are in the set of names
        List<String> upperCaseVarNames = varNames.stream().map(String::toUpperCase).collect(Collectors.toList());
        traits = programTraits.stream().filter(e -> upperCaseVarNames.contains(e.getObservationVariableName().toUpperCase())).collect(Collectors.toList());

        // If any requested observation variables are missing, throw an IllegalStateException
        if (varNames.size() != traits.size()) {
            Set<String> missingVarNames = new HashSet<>(varNames);
            missingVarNames.removeAll(traits.stream().map(TraitEntity::getObservationVariableName).collect(Collectors.toSet()));
            throw new IllegalStateException("Observation variables not found for name(s): " + String.join(ExpImportProcessConstants.COMMA_DELIMITER, missingVarNames));
        }

        return traits;
    }

    public Optional<List<ValidationError>> validateMatchedTimestamps(Set<String> observationVariableNames,
                                                                     List<Column<?>> timestampCols) {
        Optional<List<ValidationError>> ve = Optional.empty();
        // Check that each ts column corresponds to a phenotype column
        List<ValidationError> valErrs = timestampCols.stream()
                .filter(col -> !(observationVariableNames.contains(col.name().replaceFirst(ExpImportProcessConstants.TIMESTAMP_REGEX, StringUtils.EMPTY))))
                .map(col -> new ValidationError(col.name().toString(), String.format("Timestamp column %s lacks corresponding phenotype column", col.name().toString()), HttpStatus.UNPROCESSABLE_ENTITY))
                .collect(Collectors.toList());
        if (valErrs.size() > 0) {
            ve = Optional.of(valErrs);
        }

        return ve;
    }
    // TODO: used with expUnit workflow
//    public void validateObservations(PendingData pendingData,
//                                     int rowNum,
//                                     ImportContext importContext,
//                                     ExpUnitContext expUnitContext,
//                                     List<Column<?>> phenotypeCols,
//                                     CaseInsensitiveMap<String, Trait> colVarMap) {
//        for (Column<?> phenoCol : phenotypeCols) {
//            String importHash;
//            String importObsValue = phenoCol.getString(rowNum);
//
//            importHash = getImportObservationHash(
//                    pendingObsUnitByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getObservationUnitName(),
//                    getVariableNameFromColumn(phenoCol),
//                    pendingStudyByOUId.get(importRow.getObsUnitID()).getBrAPIObject().getStudyName()
//            );
//
//            validateObservation(importHash);
//        }
//    }

    // TODO: used with create workflow
//    public void validateObservations(PendingData pendingData,
//                                     int rowNum,
//                                     ImportContext importContext,
//                                     List<Column<?>> phenotypeCols,
//                                     CaseInsensitiveMap<String, Trait> colVarMap) {
//
//
//        for (Column<?> phenoCol : phenotypeCols) {
//            String importHash;
//            String importObsValue = phenoCol.getString(rowNum);
//
//            importHash = getImportObservationHash(importRow, phenoCol.name());
//
//            validateObservation(importHash);
//        }
//
//    }

    // TODO: used by both workflows
//    private void validateObservation(String importHash) {
//
//
//        String importObsValue = phenoCol.getString(rowNum);
//
//
//        // error if import observation data already exists and user has not selected to overwrite
//        if (commit && "false".equals(importRow.getOverwrite() == null ? "false" : importRow.getOverwrite()) &&
//                this.existingObsByObsHash.containsKey(importHash) &&
//                StringUtils.isNotBlank(phenoCol.getString(rowNum)) &&
//                !this.existingObsByObsHash.get(importHash).getValue().equals(phenoCol.getString(rowNum))) {
//            addRowError(
//                    phenoCol.name(),
//                    String.format("Value already exists for ObsUnitId: %s, Phenotype: %s", importRow.getObsUnitID(), phenoCol.name()),
//                    validationErrors, rowNum
//            );
//
//            // preview case where observation has already been committed and the import row ObsVar data differs from what
//            // had been saved prior to import
//        } else if (existingObsByObsHash.containsKey(importHash) && !isObservationMatched(importHash, importObsValue, phenoCol, rowNum)) {
//
//            // add a change log entry when updating the value of an observation
//            if (commit) {
//                BrAPIObservation pendingObservation = observationByHash.get(importHash).getBrAPIObject();
//                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:hh-mm-ssZ");
//                String timestamp = formatter.format(OffsetDateTime.now());
//                String reason = importRow.getOverwriteReason() != null ? importRow.getOverwriteReason() : "";
//                String prior = "";
//                if (isValueMatched(importHash, importObsValue)) {
//                    prior.concat(existingObsByObsHash.get(importHash).getValue());
//                }
//                if (timeStampColByPheno.containsKey(phenoCol.name()) && isTimestampMatched(importHash, timeStampColByPheno.get(phenoCol.name()).getString(rowNum))) {
//                    prior = prior.isEmpty() ? prior : prior.concat(" ");
//                    prior.concat(existingObsByObsHash.get(importHash).getObservationTimeStamp().toString());
//                }
//                ChangeLogEntry change = new ChangeLogEntry(prior,
//                        reason,
//                        user.getId(),
//                        timestamp
//                );
//
//                // create the changelog field in additional info if it does not already exist
//                OverwrittenData.createChangeLog(pendingObservation);
//
//                // add a new entry to the changelog
//                pendingObservation.getAdditionalInfo().get(BrAPIAdditionalInfoFields.CHANGELOG).getAsJsonArray().add(gson.toJsonTree(change).getAsJsonObject());
//            }
//
//            // preview case where observation has already been committed and import ObsVar data is the
//            // same as has been committed prior to import
//        } else if (isObservationMatched(importHash, importObsValue, phenoCol, rowNum)) {
//            BrAPIObservation existingObs = this.existingObsByObsHash.get(importHash);
//            existingObs.setObservationVariableName(phenoCol.name());
//            observationByHash.get(importHash).setState(ImportObjectState.EXISTING);
//            observationByHash.get(importHash).setBrAPIObject(existingObs);
//
//            // preview case where observation has already been committed and import ObsVar data is empty prior to import
//        } else if (!existingObsByObsHash.containsKey(importHash) && (StringUtils.isBlank(phenoCol.getString(rowNum)))) {
//            observationByHash.get(importHash).setState(ImportObjectState.EXISTING);
//        } else {
//            validateObservationValue(colVarMap.get(phenoCol.name()), phenoCol.getString(rowNum), phenoCol.name(), validationErrors, rowNum);
//
//            //Timestamp validation
//            if (timeStampColByPheno.containsKey(phenoCol.name())) {
//                Column<?> timeStampCol = timeStampColByPheno.get(phenoCol.name());
//                validateTimeStampValue(timeStampCol.getString(rowNum), timeStampCol.name(), validationErrors, rowNum);
//            }
//        }
//
//    }

    // TODO: used by both workflows
//    private void updateObservationDependencyValues(Program program) {
//        String programKey = program.getKey();
//
//        // update the observations study DbIds, Observation Unit DbIds and Germplasm DbIds
//        this.observationUnitByNameNoScope.values().stream()
//                .map(PendingImportObject::getBrAPIObject)
//                .forEach(obsUnit -> updateObservationDbIds(obsUnit, programKey));
//
//        // Update ObservationVariable DbIds
//        List<Trait> traits = getTraitList(program);
//        CaseInsensitiveMap<String, Trait> traitMap = new CaseInsensitiveMap<>();
//        for ( Trait trait: traits) {
//            traitMap.put(trait.getObservationVariableName(),trait);
//        }
//        for (PendingImportObject<BrAPIObservation> observation : this.observationByHash.values()) {
//            String observationVariableName = observation.getBrAPIObject().getObservationVariableName();
//            if (observationVariableName != null && traitMap.containsKey(observationVariableName)) {
//                String observationVariableDbId = traitMap.get(observationVariableName).getObservationVariableDbId();
//                observation.getBrAPIObject().setObservationVariableDbId(observationVariableDbId);
//            }
//        }
//    }

    // TODO: used by both workflows
//    public List<BrAPIObservation> commitNewPendingObservationsToBrAPIStore(ImportContext context, PendingData pendingData) {
//        // filter out observations with no 'value' so they will not be saved
//        List<BrAPIObservation> newObservations = ProcessorData.getNewObjects(this.observationByHash)
//                .stream()
//                .filter(obs -> !obs.getValue().isBlank())
//                .collect(Collectors.toList());
//
//        updateObservationDependencyValues(program);
//        return brAPIObservationDAO.createBrAPIObservations(newObservations, program.getId(), upload);
//
//    }

    // TODO: used by both workflows
//    public List<BrAPIObservation> commitUpdatedPendingObservationsToBrAPIStore(ImportContext importContext, PendingData pendingData) {
//        List<BrAPIObservation> updatedObservations = new ArrayList<>();
//        Map<String, BrAPIObservation> mutatedObservationByDbId = ProcessorData
//                .getMutationsByObjectId(observationByHash, BrAPIObservation::getObservationDbId);
//
//        for (Map.Entry<String, BrAPIObservation> entry : mutatedObservationByDbId.entrySet()) {
//            String id = entry.getKey();
//            BrAPIObservation observation = entry.getValue();
//            try {
//                if (observation == null) {
//                    throw new Exception("Null observation");
//                }
//                BrAPIObservation updatedObs = brAPIObservationDAO.updateBrAPIObservation(id, observation, program.getId());
//                updatedObservations.add(updatedObs);
//
//                if (updatedObs == null) {
//                    throw new Exception("Null updated observation");
//                }
//
//                if (!Objects.equals(observation.getValue(), updatedObs.getValue())
//                        || !Objects.equals(observation.getObservationTimeStamp(), updatedObs.getObservationTimeStamp())) {
//                    String message;
//                    if (!Objects.equals(observation.getValue(), updatedObs.getValue())) {
//                        message = String.format("Updated observation, %s, from BrAPI service does not match requested update %s.", updatedObs.getValue(), observation.getValue());
//                    } else {
//                        message = String.format("Updated observation timestamp, %s, from BrAPI service does not match requested update timestamp %s.", updatedObs.getObservationTimeStamp(), observation.getObservationTimeStamp());
//                    }
//                    throw new Exception(message);
//                }
//
//            } catch (ApiException e) {
//                log.error("Error updating observation: " + Utilities.generateApiExceptionLogMessage(e), e);
//                throw new InternalServerException("Error saving experiment import", e);
//            } catch (Exception e) {
//                log.error("Error updating observation: ", e);
//                throw new InternalServerException(e.getMessage(), e);
//            }
//        }
//
//        return updatedObservations;
//    }
}
