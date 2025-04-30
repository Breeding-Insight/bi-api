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
package org.breedinginsight.brapps.importer.services.processors.germplasm;

import com.google.gson.JsonElement;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;

/**
 * Utility class for managing germplasm import identifiers and pedigree relationships.
 */
public class GermplasmImportIdUtils {

    private GermplasmImportIdUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Generates an import ID for a germplasm based on its GID or entry number.
     * @param gid The germplasm ID
     * @param entryNo The entry number
     * @return The generated import ID or null if both parameters are null
     */
    public static String generateImportId(String gid, String entryNo) {
        if (gid == null && entryNo == null) return null;
        return StringUtils.isNotBlank(gid) ? "GID " + gid : "ENTRY NO " + entryNo;
    }

    /**
     * Gets the import ID for a germplasm.
     * @param germplasm The germplasm object
     * @return The import ID
     */
    public static String getImportId(BrAPIGermplasm germplasm) {
        String gid = germplasm.getAccessionNumber();
        String entryNo = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER).getAsString();
        return generateImportId(gid, entryNo);
    }

    /**
     * Gets the import ID for the mother/female parent of a germplasm.
     * @param germplasm The germplasm object
     * @return The import ID of the mother/female parent
     */
    public static String getMotherImportId(BrAPIGermplasm germplasm) {
        JsonElement motherGidElement = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_GID);
        JsonElement motherEntryNoElement = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_ENTRY_NO);
        String motherGid = !motherGidElement.isJsonNull() ? motherGidElement.getAsString() : null;
        String motherEntryNo = !motherEntryNoElement.isJsonNull() ? motherEntryNoElement.getAsString() : null;
        return generateImportId(motherGid, motherEntryNo);
    }

    /**
     * Gets the import ID for the father/male parent of a germplasm.
     * @param germplasm The germplasm object
     * @return The import ID of the father/male parent
     */
    public static String getFatherImportId(BrAPIGermplasm germplasm) {
        JsonElement fatherGidElement = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID);
        JsonElement fatherEntryNoElement = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_ENTRY_NO);
        String fatherGid = !fatherGidElement.isJsonNull() ? fatherGidElement.getAsString() : null;
        String fatherEntryNo = !fatherEntryNoElement.isJsonNull() ? fatherEntryNoElement.getAsString() : null;
        return generateImportId(fatherGid, fatherEntryNo);
    }

    /**
     * Checks if a male parent is present for a germplasm.
     * @param germplasm The germplasm object
     * @return true if a male parent is present, false otherwise
     */
    public static boolean maleParentPresent(BrAPIGermplasm germplasm) {
        boolean fatherGidNull = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID).isJsonNull();
        boolean fatherEntryNoNull = germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_ENTRY_NO).isJsonNull();
        return !fatherGidNull || !fatherEntryNoNull;
    }

    /**
     * Checks if the female parent is unknown for a germplasm.
     * @param germplasm The germplasm object
     * @return true if the female parent is unknown, false otherwise
     */
    public static boolean femaleParentUnknown(BrAPIGermplasm germplasm) {
        return germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN).getAsBoolean();
    }

    /**
     * Checks if the male parent is unknown for a germplasm.
     * @param germplasm The germplasm object
     * @return true if the male parent is unknown, false otherwise
     */
    public static boolean maleParentUnknown(BrAPIGermplasm germplasm) {
        return germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN).getAsBoolean();
    }

}