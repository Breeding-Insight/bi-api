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

package org.breedinginsight.utilities;

import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasmSynonyms;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class Utilities {

    public static <T> Optional<T> findInList(List<T> checkList, T objectToCheck, Function<T, UUID> getterMethod){

        Optional<T> existingObject = checkList.stream()
                .filter(p -> getterMethod.apply(p).equals(getterMethod.apply(objectToCheck)))
                .findFirst();
        return existingObject;
    }

    /**
     * Case insensitive search for string in string list
     *
     * @param target - string to search for
     * @param list - list of strings to search in
     * @return true if case insensitive match, false otherwise
     */
    public static boolean containsCaseInsensitive(String target, List<String> list){
        return list.stream().anyMatch(x -> x.equalsIgnoreCase(target));
    }

    /**
     * Formats a string to: <code>&lt;original&gt; [&lt;programKey&gt;]</code>.<br><br>
     *
     * If <code>additionalKeyData</code> is populated, then returns: <code>&lt;original&gt; [&lt;programKey&gt;-&lt;additionalKeyData&gt;]</code>
     *
     * @param original
     * @param programKey
     * @param additionalKeyData
     * @return the formatted string
     */
    public static String appendProgramKey(String original, String programKey, String additionalKeyData) {
        if(StringUtils.isNotBlank(additionalKeyData)) {
            return String.format("%s [%s-%s]", original, programKey, additionalKeyData);
        } else {
            return String.format("%s [%s]", original, programKey);
        }
    }

    public static String appendProgramKey( String original, String programKey ){
        return appendProgramKey( original, programKey, null);
    }

    /**
     * Remove program key from a string. Returns a new value instead of altering original string.
     *
     * @param original
     * @param programKey
     * @param additionalKeyData
     * @return
     */
    public static String removeProgramKey(String original, String programKey, String additionalKeyData) {
        if(StringUtils.isNotBlank(additionalKeyData)) {
            String keyValue = String.format(" [%s-%s]", programKey, additionalKeyData);
            return original.replace(keyValue, "");
        } else {
            String keyValue = String.format(" [%s]", programKey);
            return original.replace(keyValue, "");
        }
    }

    /**
     * Remove program key from a string. Returns a new value instead of altering original string.
     *
     * @param original
     * @param programKey
     * @return
     */
    public static String removeProgramKey(String original, String programKey) {
        return removeProgramKey(original, programKey, null);
    }

    public static String removeProgramKeyAndUnknownAdditionalData(String original, String programKey) {
        String keyValueRegEx = String.format(" \\[%s\\-.*\\]", programKey);
        String stripped =  original.replaceAll(keyValueRegEx, "");
        return stripped;
    }

    public static String generateApiExceptionLogMessage(ApiException e) {
        return new StringBuilder("BrAPI Exception: \n\t").append("message: ")
                                                         .append(e.getMessage())
                                                         .append("\n\t")
                                                         .append("body: ")
                                                         .append(e.getResponseBody())
                                                         .append("\n\t")
                                                         .append("code: ")
                                                         .append(e.getCode())
                                                         .toString();
    }

    public static String generateReferenceSource(String referenceSourceBase, ExternalReferenceSource referenceSource) {
        return String.format("%s/%s",referenceSourceBase, referenceSource.getName());
    }

    public static Optional<BrAPIExternalReference> getExternalReference(List<BrAPIExternalReference> externalReferences, String source) {
        if(externalReferences == null) {
            return Optional.empty();
        }
        return externalReferences.stream().filter(externalReference -> externalReference.getReferenceSource().equals(source)).findFirst();
    }

    /**
     * For a list of items, if the list has only one item, return that item, otherwise return an empty {@link Optional}
     * @param items {@link List} of items
     * @return Optional of type T or empty Optional
     */
    public static <T> Optional<T> getSingleOptional(List<T> items) {
        if(items.size() == 1) {
            return Optional.of(items.get(0));
        } else {
            return Optional.empty();
        }
    }
}
