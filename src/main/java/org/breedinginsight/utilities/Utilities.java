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
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.flywaydb.core.api.migration.Context;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
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
     * Remove unknown program key from a string. Returns a new value instead of altering original string.
     *
     * @param original - The string with a [program key]
     * @return - the original string without the [program key]
     */
    public static String removeUnknownProgramKey(String original) {
        return original.replaceAll("\\[.*\\]", "").trim();
    }

    /**
     * Removes the program key from a string with any accession number.
     *
     * @param str The string to remove the program key from
     * @param programKey The program key to remove
     * @return The modified string
     */
    public static String removeProgramKeyAnyAccession(String str, String programKey) {
        return str.replaceAll("\\[" + programKey + "-.*\\]", "").trim();
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
        String keyValue;
        if(StringUtils.isNotBlank(additionalKeyData)) {
            keyValue = String.format(" [%s-%s]", programKey, additionalKeyData);
        } else {
            keyValue = String.format(" [%s]", programKey);
        }
        return original.replace(keyValue, "");
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

    /**
     * Remove program key from fields visible on the front end. Mutates the original object and returns it.
     *
     * @param brapiInstance Object, an instance of a BrAPI Object
     * @param brapiClass Class, the BrAPI class
     * @param program
     * @return Object, BrAPI instance formatted for display
     */
    public static Object formatBrapiObjForDisplay(Object brapiInstance, Class brapiClass, Program program) throws RuntimeException {
        List<String> displayFields = new ArrayList<>(List.of(
                "trialName",
                "studyName",
                "germplasmName",
                "locationName",
                "observationUnitName",
                "observationVariableName"));
        List<Field> fields = List.of(brapiClass.getDeclaredFields());
        for (Field field : fields) {
            if (displayFields.contains(field.getName())) {
                try {
                    field.setAccessible(true);

                    // remove either of possible key formats, [%s-%s] and [%s]
                    String valueSansKeyAndInfo = removeProgramKeyAndUnknownAdditionalData((String) field.get(brapiInstance), program.getKey());
                    String valueSansKey = removeProgramKey(valueSansKeyAndInfo, program.getKey());

                    // set the value without key or additional info
                    field.set(brapiInstance, valueSansKey);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return brapiInstance;
    }

    /**
     * \s*: Matches zero or more whitespace characters before the opening bracket.
     * \[: Matches the opening square bracket [. The backslash is used to escape the special meaning of [ in regex.
     * .*?: Matches any character (except newline) zero or more times, non-greedily.
     * . matches any character except newline.
     * * means "zero or more times".
     * ? makes the matching non-greedy, so it stops at the first closing bracket.
     * \]: Matches the closing square bracket ]. Again, the backslash is used to escape it.
     * \s*: Matches zero or more whitespace characters after the closing bracket.
     * @param original
     * @param programKey
     * @return
     */
    public static String removeProgramKeyAndUnknownAdditionalData(String original, String programKey) {
        String keyValueRegEx = String.format("\\s*\\[%s-.*?\\]\\s*", programKey);
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

    public static Optional<BrAPIExternalReference> getExternalReference(List<BrAPIExternalReference> externalReferences, String referenceSourceBase, ExternalReferenceSource referenceSource) {
        return getExternalReference(externalReferences, generateReferenceSource(referenceSourceBase, referenceSource));
    }

    public static void addReference(List<BrAPIExternalReference> refs, UUID uuid, String referenceBaseNameSource, ExternalReferenceSource refSourceName) {
        BrAPIExternalReference reference = new BrAPIExternalReference();
        reference.setReferenceSource(String.format("%s/%s", referenceBaseNameSource, refSourceName.getName()));
        reference.setReferenceId(uuid.toString());
        refs.add(reference);
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

    /**
     * For a possibly unsafe file name, return a new String that is safe across platforms.
     * @param name a possibly unsafe file name
     * @return a portable file name
     */
    public static String makePortableFilename(String name) {
        StringBuilder sb = new StringBuilder();
        char c;
        char last_appended = '_';
        int i = 0;
        while (i < name.length()) {
            c = name.charAt(i);
            if (isSafeChar(c)) {
                sb.append(c);
                last_appended = c;
            }
            else {
                // Replace illegal chars with '_', but prevent repeat underscores.
                if (last_appended != '_') {
                    sb.append('_');
                    last_appended = '_';
                }
            }
            ++i;
        }

        return sb.toString();
    }

    /**
     * For only the context of a specific flyway migration, return a list of all Deltabreed programs.
     * @param context the context relevant to a Java-based migration
     * @param defaultUrl the url for the default BrAPI service
     * @return a list of all Deltabreed programs
     */
    public static List<Program> getAllProgramsFlyway(Context context, String defaultUrl) throws Exception {
        List<Program> programs = new ArrayList<>();
        try (Statement select = context.getConnection().createStatement()) {
            try (ResultSet rows = select.executeQuery("SELECT id, brapi_url, key FROM program where active = true ORDER BY id")) {
                while (rows.next()) {
                    Program program = new Program();
                    program.setId(UUID.fromString(rows.getString(1)));
                    String brapi_url = rows.getString(2);
                    if (brapi_url == null) brapi_url = defaultUrl;
                    program.setBrapiUrl(brapi_url);
                    program.setKey(rows.getString(3));
                    programs.add(program);
                }
            }
        }
        return programs;
    }

    /**
     * Returns the input string with the first character capitalized, the rest lower cased.
     * Note: does not account for whitespace, does not capitalize multiple words.
     */
    public static String capitalize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    private static boolean isSafeChar(char c) {
        // Check if c is in the portable filename character set.
        // See https://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap03.html#tag_03_282
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
    }
}
