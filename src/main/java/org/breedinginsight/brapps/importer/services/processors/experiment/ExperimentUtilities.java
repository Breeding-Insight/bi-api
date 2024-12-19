/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.brapps.importer.services.processors.experiment;

import com.github.filosganga.geogson.gson.GeometryAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.reactivex.functions.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.PendingData;
import org.breedinginsight.brapps.importer.services.processors.experiment.create.model.ProcessedPhenotypeData;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import tech.tablesaw.columns.Column;

import javax.inject.Singleton;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class ExperimentUtilities {

    public static final CharSequence COMMA_DELIMITER = ",";
    public static final String TIMESTAMP_PREFIX = "TS:";
    public static final String TIMESTAMP_REGEX = "^"+TIMESTAMP_PREFIX+"\\s*";
    public static final String MIDNIGHT = "T00:00:00-00:00";
    public static final String MULTIPLE_EXP_TITLES = "File contains more than one Experiment Title";
    public static final String PREEXISTING_EXPERIMENT_TITLE = "Experiment Title already exists";
    public static final String MISSING_OBS_UNIT_ID_ERROR = "Experimental entities are missing ObsUnitIDs";
    public static final String UNMATCHED_COLUMN = "Ontology term(s) not found: ";




    Gson gson;

    public ExperimentUtilities() {
        this.gson = new GsonBuilder().registerTypeAdapterFactory(new GeometryAdapterFactory()).create();
    }

    /**
     * Checks if the provided list contains any invalid members for the specified class.
     *
     * @param list The list to be checked for invalid members
     * @param clazz The class to check for instance validity
     * @return true if the list is null, empty, or contains any member that is not an instance of the specified class; false otherwise
     */
    public boolean isInvalidMemberListForClass(List<?> list, Class<?> clazz) {
        // Check if the input list is null, empty, or contains any member that is not an instance of the specified class
        return list == null || list.isEmpty() || !list.stream().allMatch(clazz::isInstance);
    }

    /**
     * This method creates a deep copy of an object using Gson library in Java 8.
     * It takes an object of type T and its corresponding class to clone.
     * @param obj the object to clone
     * @param clazz the class of the object to clone
     * @return an Optional containing the cloned object if successful, otherwise an empty Optional
     * @throws JsonSyntaxException if there is an issue with JSON syntax during the cloning process
     */
    public <T> Optional<T> clone(T obj, Class<T> clazz) {
        try {
            // Convert the object to JSON string and then parse it back to the specified class
            return Optional.ofNullable(gson.fromJson(gson.toJson(obj), clazz));
        } catch (JsonSyntaxException e) {
            // Return an empty Optional if there is a JsonSyntaxException
            return Optional.empty();
        }
    }

    /**
     * Copies the pending BrAPI objects from the workflow cache map based on the provided import object status.
     *
     * @param pendingCacheMap a map containing pending import objects with generic values V and T
     * @param clazz the class type of the BrAPI object to be cloned
     * @param status the import object state to filter the pending objects
     * @return a list of cloned BrAPI objects that are in the specified import object state
     */
    public <T, V> List<T> copyWorkflowCachePendingBrAPIObjects(Map<V, PendingImportObject<T>> pendingCacheMap,
                                                               Class<T> clazz,
                                                               ImportObjectState status) {
        // Filter the pending import objects by checking if the object is not null and has the specified status
        return pendingCacheMap.values().stream()
                .filter(preview -> preview != null && preview.getState() == status)
                // Map each pending import object to its corresponding BrAPI object
                .map(PendingImportObject::getBrAPIObject)
                // Clone the BrAPI object with the provided class type
                .map(brApiObject -> clone(brApiObject, clazz))
                // Filter out any empty optionals
                .filter(Optional::isPresent)
                // Unwrap the optional value
                .map(Optional::get)
                // Collect the cloned BrAPI objects into a list
                .collect(Collectors.toList());
    }

    /**
     * Converts a list of BrAPI imports to a list of ExperimentObservations.
     *
     * This function takes a list of BrAPIImport objects representing trial imports and converts them
     * to ExperimentObservation objects. It utilizes Java 8 stream API to map each BrAPIImport object
     * to an ExperimentObservation object and collects the results into a new list.
     *
     * @param importRows a list of BrAPIImport objects representing trial imports to be converted
     * @return a list of ExperimentObservation objects containing the converted data
     */
    public static List<ExperimentObservation> importRowsToExperimentObservations(List<BrAPIImport> importRows) {
        return importRows.stream()
                .map(trialImport -> (ExperimentObservation) trialImport)
                .collect(Collectors.toList());
    }

    /**
     * This method generates a unique key for an observation unit based on the environment and experimental unit ID.
     *
     * @param importRow the ExperimentObservation object containing the environment and experimental unit ID
     * @return a String representing the unique key for the observation unit
     */
    public static String createObservationUnitKey(ExperimentObservation importRow) {
        // Extract the environment and experimental unit ID from the ExperimentObservation object
        // and pass them to the createObservationUnitKey method
        return createObservationUnitKey(importRow.getEnv(), importRow.getExpUnitId());
    }

    /**
     * Create Observation Unit Key
     *
     * This method takes in the name of a study and the name of an observation unit and concatenates them to create a unique key.
     *
     * If one or both of the inputs is null, returns an empty string since not a valid combination
     *
     * @param studyName The name of the study
     * @param obsUnitName The name of the observation unit
     * @return A string representing the unique key formed by concatenating the study name and observation unit name
     */
    public static String createObservationUnitKey(String studyName, String obsUnitName) {
        // Concatenate the study name and observation unit name to create the unique key
        if (studyName != null && obsUnitName != null) {
            return studyName + obsUnitName;
        } else {
            return "";
        }
    }

// Module/Script-level documentation
    /**
     * This method is used to retrieve the value from a map if the map contains only one entry.
     * This method is particularly useful when dealing with scenarios where a single entry is expected.
     * It throws an UnprocessableEntityException if the map does not contain exactly one entry, providing a custom error message for clarity.
     * Usage:
     * Map<String, Integer> exampleMap = new HashMap<>();
     * exampleMap.put("exampleKey", 5);
     * Integer value = getSingleEntryValue(exampleMap, "Map should contain exactly one entry.");
     */

    /**
     * Retrieves the single value from a given map, if the map contains exactly one key-value pair.
     *
     * @param map The map from which to retrieve the single value.
     * @return An Optional containing the single value if the map contains only one key-value pair,
     *         or an empty Optional if the map is empty or contains more than one entry.
     *
     * Input:
     * - map: The map from which to retrieve the single value.
     *
     * Output:
     * - An Optional containing the single value if the map contains exactly one key-value pair,
     *   or an empty Optional if the map is empty or contains more than one entry.
     *
     * Side Effects:
     * - None
     *
     * Usage:
     * if you have a map and you want to retrieve the single value associated with a key, you can use
     * this function to ensure that the map contains only one entry before retrieving the value.
     */
    public static <K, V> Optional<V> getSingleEntryValue(Map<K, V> map) {
        Optional<V> value = Optional.empty();
        if (map.size() == 1) {
            value = Optional.ofNullable(map.values().iterator().next());
        }
        return value;
    }

    /*
     * Add a given year to the additionalInfo field of the BrAPIStudy, if it does not already exist.
     *
     * @param program the program to which the study belongs
     * @param study the BrAPIStudy object to which the year should be added
     * @param year the year to be added to the additionalInfo field
     *
     * This method checks if the additionalInfo field of the BrAPIStudy object is null, and if so, initializes it with a new JsonObject.
     * Then, it checks if the ENV_YEAR key already exists in the additionalInfo object, and if not, adds the given year with the key ENV_YEAR.
     *
     * @return void
     */

    /* Module Description
     * This module contains a method that adds a given year to the additionalInfo field of a BrAPIStudy object within a program's context.
     * The purpose of this method is to provide a convenient way to store and retrieve additional information related to the study.
     *
     * Usage:
     * To add a year to the additionalInfo field of a BrAPIStudy object, call this method passing the program, study, and year as parameters.
     */

    /* Side effects:
     * This method mutates the state of the BrAPIStudy object by adding the given year to its additionalInfo field.
     */
    public static boolean validDateTimeValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    public static String getObservationHash(String observationUnitName, String variableName, String studyName) {
        String concat = DigestUtils.sha256Hex(observationUnitName) +
                DigestUtils.sha256Hex(variableName) +
                DigestUtils.sha256Hex(StringUtils.defaultString(studyName));
        return DigestUtils.sha256Hex(concat);
    }

    /*
     * this finds the YEAR from the season list on the BrAPIStudy and then
     * will add the year to the additionalInfo-field of the BrAPIStudy
     * */
    public static void addYearToStudyAdditionalInfo(Program program, BrAPIStudy study) {
        JsonObject additionalInfo = study.getAdditionalInfo();

        //if it is already there, don't add it.
        if(additionalInfo==null || additionalInfo.get(BrAPIAdditionalInfoFields.ENV_YEAR)==null) {
            String year = study.getSeasons().get(0);
            addYearToStudyAdditionalInfo(program, study, year);
        }
    }

    /*
     * this will add the given year to the additionalInfo field of the BrAPIStudy (if it does not already exist)
     * */
    public static void addYearToStudyAdditionalInfo(Program program, BrAPIStudy study, String year) {
        JsonObject additionalInfo = study.getAdditionalInfo();
        if (additionalInfo==null){
            additionalInfo = new JsonObject();
            study.setAdditionalInfo(additionalInfo);
        }
        if( additionalInfo.get(BrAPIAdditionalInfoFields.ENV_YEAR)==null) {
            additionalInfo.addProperty(BrAPIAdditionalInfoFields.ENV_YEAR, year);
        }
    }

    /**
     * This method is responsible for collating unique ObsUnit IDs from the provided context data.
     *
     * @param context the AppendOverwriteMiddlewareContext containing the import rows to process
     * @return a Set of unique ObsUnit IDs collated from the import rows
     * @throws IllegalStateException if any ObsUnit ID is repeated in the import rows
     * @throws HttpStatusException if there is a mix of ObsUnit IDs for some but not all rows
     */
    public static Set<String> collateReferenceOUIds(AppendOverwriteMiddlewareContext context) throws HttpStatusException, IllegalStateException {
        // Initialize variables to track the presence of ObsUnit IDs
        Set<String> referenceOUIds = new HashSet<>();
        boolean hasNoReferenceUnitIds = true;
        boolean hasAllReferenceUnitIds = true;

        // Iterate through the import rows to process ObsUnit IDs
        for (int rowNum = 0; rowNum < context.getImportContext().getImportRows().size(); rowNum++) {
            ExperimentObservation importRow = (ExperimentObservation) context.getImportContext().getImportRows().get(rowNum);

            // Check if ObsUnitID is blank
            if (importRow.getObsUnitID() == null || importRow.getObsUnitID().isBlank()) {
                // Set flag to indicate missing ObsUnit ID for current row
                hasAllReferenceUnitIds = false;
            } else if (referenceOUIds.contains(importRow.getObsUnitID())) {
                // Throw exception if ObsUnitID is repeated
                throw new IllegalStateException("ObsUnitId is repeated: " + importRow.getObsUnitID());
            } else {
                // Add ObsUnitID to referenceOUIds
                referenceOUIds.add(importRow.getObsUnitID());
                // Set flag to indicate presence of ObsUnit ID
                hasNoReferenceUnitIds = false;
            }
        }

        if (!hasNoReferenceUnitIds && !hasAllReferenceUnitIds) {
            // Throw exception if there is a mix of ObsUnit IDs for some but not all rows
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ExpImportProcessConstants.ErrMessage.MISSING_OBS_UNIT_ID_ERROR.getValue());
        }

        return referenceOUIds;
    }

    /**
     * This method sorts a list of items based on a list of sorted fields in ascending order using Java 8 functionality.
     *
     * @param sortedFields a list of strings representing the fields to sort by
     * @param unsortedItems a list of items of generic type T to be sorted
     * @param fieldGetter a Function object that extracts the string field from an item of type T
     * @return a sorted list of items of type T based on the specified fields
     * @throws RuntimeException if there are any exceptions encountered during sorting
     */
    public <T> List<T> sortByField(List<String> sortedFields, List<T> unsortedItems, Function<T, String> fieldGetter) {
        // Create a case-insensitive map to store the sort order of fields
        CaseInsensitiveMap<String, Integer> sortOrder = new CaseInsensitiveMap<>();

        // Populate the sortOrder map with the fields and their respective indices from the sortedFields list
        for (int i = 0; i < sortedFields.size(); i++) {
            sortOrder.put(sortedFields.get(i), i);
        }

        // Sort the unsortedItems list using a lambda expression to compare items based on the order of specified fields
        unsortedItems.sort((i1, i2) -> {
            try {
                // Extract the field values of the items using the fieldGetter function
                String field1 = fieldGetter.apply(i1);
                String field2 = fieldGetter.apply(i2);

                // Compare the indices of the fields in sortOrder and return the result
                return Integer.compare(sortOrder.get(field1), sortOrder.get(field2));
            } catch (Exception e) {
                // Throw a runtime exception if any error occurs during sorting
                throw new RuntimeException(e);
            }
        });

        // Return the sorted list of items
        return unsortedItems;
    }

    /**
     * Constructs a list of BrAPIExternalReference objects for various entities using the given parameters.
     *
     * @param program the program entity for which external references are to be constructed
     * @param referenceSourceBaseName the base name for the reference source
     * @param trialId the UUID of the trial entity
     * @param datasetId the UUID of the dataset entity
     * @param studyId the UUID of the study entity
     * @param obsUnitId the UUID of the observation unit entity
     * @param observationId the UUID of the observation entity
     * @return a list of BrAPIExternalReference objects representing the external references
     */
    public List<BrAPIExternalReference> constructBrAPIExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId, UUID datasetId, UUID studyId, UUID obsUnitId, UUID observationId) {
        List<BrAPIExternalReference> refs = new ArrayList<>();

        // Add reference for the program entity
        addReference(refs, program.getId(), referenceSourceBaseName, ExternalReferenceSource.PROGRAMS);

        // Add reference for the trial entity if available
        if (trialId != null) {
            addReference(refs, trialId, referenceSourceBaseName, ExternalReferenceSource.TRIALS);
        }

        // Add reference for the dataset entity if available
        if (datasetId != null) {
            addReference(refs, datasetId, referenceSourceBaseName, ExternalReferenceSource.DATASET);
        }

        // Add reference for the study entity if available
        if (studyId != null) {
            addReference(refs, studyId, referenceSourceBaseName, ExternalReferenceSource.STUDIES);
        }

        // Add reference for the observation unit entity if available
        if (obsUnitId != null) {
            addReference(refs, obsUnitId, referenceSourceBaseName, ExternalReferenceSource.OBSERVATION_UNITS);
        }

        // Add reference for the observation entity if available
        if (observationId != null) {
            addReference(refs, observationId, referenceSourceBaseName, ExternalReferenceSource.OBSERVATIONS);
        }

        return refs;
    }

    /**
     * Adds a new reference to the given list of BrAPIExternalReference objects.
     *
     * @param refs the list of BrAPIExternalReference objects to which the new reference will be added
     * @param uuid the UUID to set as the reference ID for the new BrAPIExternalReference
     * @param referenceBaseNameSource the base name for the reference source
     * @param refSourceName the source of the external reference
     */
    private void addReference(List<BrAPIExternalReference> refs, UUID uuid, String referenceBaseNameSource, ExternalReferenceSource refSourceName) {
        // Create a new BrAPIExternalReference object
        BrAPIExternalReference reference = new BrAPIExternalReference();

        // Set the reference source as a combination of reference base name source and external reference source
        reference.setReferenceSource(String.format("%s/%s", referenceBaseNameSource, refSourceName.getName()));

        // Set the reference ID as the UUID converted to a string
        reference.setReferenceID(uuid.toString());

        // Add the new reference to the list of references
        refs.add(reference);
    }
    /**
     * Module overview: This module provides a method to add a new reference to a list of BrAPIExternalReference objects.
     * The method takes in the list of references, a UUID, reference base name source, and external reference source.
     * It creates a new BrAPIExternalReference object, sets the reference source and ID, and adds it to the list of references.
     * This function is useful when dealing with external references in BrAPI-related operations.
     * Usage: Call this method with the required parameters to add a new reference to the existing list of references.
     */


    public static String getImportObservationHash(ExperimentObservation importRow, String variableName) {
        return getObservationHash(createObservationUnitKey(importRow), variableName, importRow.getEnv());
    }

    public static String getVariableNameFromColumn(Column<?> column) {
        // TODO: timestamp stripping?
        return column.name();
    }

    // TODO: common validation stuff, could probably be moved somewhere more specific to validation
    public static void addRowError(String field, String errorMessage, ValidationErrors validationErrors, int rowNum) {
        ValidationError ve = new ValidationError(field, errorMessage, HttpStatus.UNPROCESSABLE_ENTITY);
        validationErrors.addError(rowNum + 2, ve);  // +2 instead of +1 to account for the column header row.
    }

    // TODO: will have different pending data objects between workflows so not totally reusable as-is
    // could probably just pass in actual underlying maps
    public static boolean isObservationMatched(ProcessedPhenotypeData phenotypeData,
                                               PendingData pendingData,
                                               String observationHash,
                                               String value,
                                               Column phenoCol,
                                               Integer rowNum) {
        Map<String, Column<?>> timeStampColByPheno = phenotypeData.getTimeStampColByPheno();

        if (timeStampColByPheno.isEmpty() || !timeStampColByPheno.containsKey(phenoCol.name())) {
            return isValueMatched(pendingData, observationHash, value);
        } else {
            String importObsTimestamp = timeStampColByPheno.get(phenoCol.name()).getString(rowNum);
            return isTimestampMatched(pendingData, observationHash, importObsTimestamp) && isValueMatched(pendingData, observationHash, value);
        }
    }

    // TODO: will have different pending data objects between workflows so not totally reusable as-is
    // could probably just pass in actual underlying maps
    public static boolean isValueMatched(PendingData pendingData, String observationHash, String value) {
        Map<String, BrAPIObservation> existingObsByObsHash = pendingData.getExistingObsByObsHash();

        if (!existingObsByObsHash.containsKey(observationHash) || existingObsByObsHash.get(observationHash).getValue() == null) {
            return value == null;
        }
        return existingObsByObsHash.get(observationHash).getValue().equals(value);
    }

    // TODO: will have different pending data objects between workflows so not totally reusable as-is
    // could probably just pass in actual underlying maps
    public static boolean isTimestampMatched(PendingData pendingData, String observationHash, String timeStamp) {
        OffsetDateTime priorStamp = null;
        Map<String, BrAPIObservation> existingObsByObsHash = pendingData.getExistingObsByObsHash();

        if(existingObsByObsHash.get(observationHash)!=null){
            priorStamp = existingObsByObsHash.get(observationHash).getObservationTimeStamp();
        }
        if (priorStamp == null) {
            return timeStamp == null;
        }
        boolean isMatched = false;
        try {
            isMatched = priorStamp.isEqual(OffsetDateTime.parse(timeStamp));
        } catch(DateTimeParseException e){
            //if timestamp is invalid DateTime not equal to validated priorStamp
            log.error(e.getMessage(), e);
        }
        return isMatched;
    }

    public static void validateObservationValue(Trait variable, String value,
                                          String columnHeader, ValidationErrors validationErrors, int row) {
        if (StringUtils.isBlank(value)) {
            log.debug(String.format("skipping validation of observation because there is no value.\n\tvariable: %s\n\trow: %d", variable.getObservationVariableName(), row));
            return;
        }

        if (isNAObservation(value)) {
            log.debug(String.format("skipping validation of observation because it is NA.\n\tvariable: %s\n\trow: %d", variable.getObservationVariableName(), row));
            return;
        }

        switch (variable.getScale().getDataType()) {
            case NUMERICAL:
                Optional<BigDecimal> number = validNumericValue(value);
                if (number.isEmpty()) {
                    addRowError(columnHeader, "Non-numeric text detected", validationErrors, row);
                } else if (!validNumericRange(number.get(), variable.getScale())) {
                    addRowError(columnHeader, "Value outside of min/max range detected", validationErrors, row);
                }
                break;
            case DATE:
                if (!validDateValue(value)) {
                    addRowError(columnHeader, "Incorrect date format detected. Expected YYYY-MM-DD", validationErrors, row);
                }
                break;
            case ORDINAL:
                if (!validCategory(variable.getScale().getCategories(), value)) {
                    addRowError(columnHeader, "Undefined ordinal category detected", validationErrors, row);
                }
                break;
            case NOMINAL:
                if (!validCategory(variable.getScale().getCategories(), value)) {
                    addRowError(columnHeader, "Undefined nominal category detected", validationErrors, row);
                }
                break;
            case TEXT:
                if (!validText(value)) {
                    addRowError(columnHeader, "'Null' is not a valid value", validationErrors, row);
                }
                break;
            default:
                break;
        }

    }

    public static Optional<BigDecimal> validNumericValue(String value) {
        BigDecimal number;
        try {
            number = new BigDecimal(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(number);
    }

    public static boolean validNumericRange(BigDecimal value, Scale validValues) {
        // account for empty min or max in valid determination
        return (validValues.getValidValueMin() == null || value.compareTo(BigDecimal.valueOf(validValues.getValidValueMin())) >= 0) &&
                (validValues.getValidValueMax() == null || value.compareTo(BigDecimal.valueOf(validValues.getValidValueMax())) <= 0);
    }

    public static boolean validDateValue(String value) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        try {
            formatter.parse(value);
        } catch (DateTimeParseException e) {
            return false;
        }
        return true;
    }

    public static boolean validCategory(List<BrAPIScaleValidValuesCategories> categories, String value) {
        Set<String> categoryValues = categories.stream()
                .map(category -> category.getValue().toLowerCase())
                .collect(Collectors.toSet());
        return categoryValues.contains(value.toLowerCase());
    }

    public static boolean validText(String value){
        return (value != null) && (!value.equalsIgnoreCase("null"));
    }

    public static boolean isNAObservation(String value){
        return value.equalsIgnoreCase("NA");
    }

    public static void validateTimeStampValue(String value,
                                        String columnHeader, ValidationErrors validationErrors, int row) {
        if (StringUtils.isBlank(value)) {
            log.debug(String.format("skipping validation of observation timestamp because there is no value.\n\tvariable: %s\n\trow: %d", columnHeader, row));
            return;
        }
        if (!validDateValue(value) && !validDateTimeValue(value)) {
            addRowError(columnHeader, "Incorrect datetime format detected. Expected YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+hh:mm", validationErrors, row);
        }

    }

}