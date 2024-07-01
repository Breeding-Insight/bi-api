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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.reactivex.functions.Function;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;



@Singleton
public class ExperimentUtilities {

    public static final CharSequence COMMA_DELIMITER = ",";
    public static final String TIMESTAMP_PREFIX = "TS:";

    Gson gson;

    public ExperimentUtilities() {
        this.gson = new Gson();
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
     * Retrieves a list of new objects of type T from the provided map of pending import objects by filtering out null previews and objects with state other than NEW,
     * mapping the BrAPI object from each preview, cloning it to the specified class type, and collecting the non-empty results into a list.
     *
     * @param objectsByName a map of pending import objects with V keys and PendingImportObject<T> values
     * @param clazz the target class type for cloning the BrAPI object
     * @param <T> the type of new objects to be retrieved
     * @param <V> the type of keys in the map of pending import objects
     * @return a list of cloned new objects of type T extracted from the input map
     */
    public <T, V> List<T> getNewObjects(Map<V, PendingImportObject<T>> objectsByName, Class<T> clazz) {
        return objectsByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .map(PendingImportObject::getBrAPIObject)
                .map(b->clone(b, clazz))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Copies mutated objects from a cache map to a new list.
     * Only objects with ImportObjectState MUTATED are included in the copied list.
     *
     * @param pendingCacheMap a map containing PendingImportObject objects to be copied
     * @param clazz a Class object representing the type of objects to be copied
     * @return a List of copied objects of type T
     */
    public <T, V> List<T> copyMutationsFromCache(Map<V, PendingImportObject<T>> pendingCacheMap, Class<T> clazz) {
        return pendingCacheMap.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.MUTATED)
                .map(PendingImportObject::getBrAPIObject)
                .map(b -> clone(b, clazz))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
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
     * Retrieves mutations by object ID from a Map of PendingImportObject, filtering based on the object state and applying a DB ID filter.
     *
     * @param objectsByName A Map with values of type PendingImportObject, used for retrieving the mutations.
     * @param dbIdFilter A Function that filters the objects based on their DB ID.
     * @param clazz The Class type for the objects in the Map.
     * @param <T> Type parameter for the objects in the Map.
     * @param <V> Type parameter for the keys in the Map.
     * @return A Map of String keys (DB IDs) and objects of type T as values based on the filter logic.
     * @throws RuntimeException if an exception occurs while applying the DB ID filter to an object.
     */
    public <T, V> Map<String, T> getMutationsByObjectId(Map<V, PendingImportObject<T>> objectsByName, Function<T, String> dbIdFilter, Class<T> clazz) {
        return objectsByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.MUTATED)
                .map(PendingImportObject::getBrAPIObject)
                .map(b -> clone(b, clazz))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors
                        .toMap(brapiObj -> {
                                    try {
                                        return dbIdFilter.apply(brapiObj);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                },
                                brapiObj -> brapiObj));
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
     * @param studyName The name of the study
     * @param obsUnitName The name of the observation unit
     * @return A string representing the unique key formed by concatenating the study name and observation unit name
     */
    public static String createObservationUnitKey(String studyName, String obsUnitName) {
        // Concatenate the study name and observation unit name to create the unique key
        return studyName + obsUnitName;
    }

    /**
     * Returns the single value from the provided map if it contains exactly one entry.
     *
     * @param map the map from which to extract the single value
     * @param message the message to be included in the exception thrown if the map does not contain exactly one entry
     * @return the single value from the map
     * @throws UnprocessableEntityException if the map does not contain only one entry
     */
    public <K, V> V getSingleEntryValue(Map<K, V> map, String message) throws UnprocessableEntityException {
        if (map.size() != 1) {
            throw new UnprocessableEntityException(message);
        }
        return map.values().iterator().next();
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

    /*
     * this will add the given year to the additionalInfo field of the BrAPIStudy (if it does not already exist)
     * */
    public void addYearToStudyAdditionalInfo(Program program, BrAPIStudy study, String year) {
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
    public static Set<String> collateReferenceOUIds(AppendOverwriteMiddlewareContext context) {
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
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ExpImportProcessConstants.ErrMessage.MISSING_OBS_UNIT_ID_ERROR);
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
}
