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

package org.breedinginsight.brapps.importer.model.base;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.BrAPIGermplasmSynonyms;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="Germplasm", name="Germplasm",
        description = "A germplasm object corresponds to a non-physical entity and is used to track a unique genetic composition. This is commonly used for populations.")
public class Germplasm implements BrAPIObject {

    public static final String GERMPLASM_NAME_TARGET = "germplasmName";

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="germplasmName", name="Germplasm Name", description = "Name of germplasm")
    private String germplasmName;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="breedingMethod", name="Breeding Method", description = "The breeding method name or code")
    private String breedingMethod;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="germplasmSource", name="Source", description = "The germplasm origin. If External UID present, assumed to be the source associated with the External UID.")
    private String germplasmSource;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="externalUID", name="External UID", description = "External UID")
    private String externalUID;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="synonyms", name="Synonyms", description = "Optional list of germplasm synonyms separated by semicolons (;).")
    private String synonyms;

    @ImportFieldType(type= ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id="entryNo", name="Entry No.", description = "The order of germplasm in the import list ( 1,2,3,….n). If no entry  number is specified in the import germplasm list, the database will assign entry number upon import.")
    private String entryNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="femaleParentAccessionNumber", name="Female Parent Accession Number", description = "The accession number (GID) of the female parent of the germplasm.")
    private String femaleParentAccessionNumber;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="maleParentAccessionNumber", name="Male Parent Accession Number", description = "The accession number (GID) of the male parent of the germplasm.")
    private String maleParentAccessionNumber;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="femaleParentEntryNo", name="Female Parent Entry Number", description = "The entry number of the female parent of the germplasm. Used to import offspring with progenitors not yet in the database.")
    private String femaleParentEntryNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="maleParentEntryNo", name="Male Parent Entry Number", description = "The entry number of the male parent of the germplasm. Used to import offspring with progenitors not yet in the database.")
    private String maleParentEntryNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="germplasmPUI", name="Germplasm Permanent Unique Identifier", description = "The Permanent Unique Identifier which represents a germplasm from the source or donor.")
    private String germplasmPUI;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="acquisitionDate", name="Acquisition Date", description = "The date this germplasm was acquired by the genebank.")
    private String acquisitionDate;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="countryOfOrigin", name="Country of Origin", description = "Two letter code for the country of origin.")
    private String countryOfOrigin;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="collection", name="Family Name", description = "The name of the family this germplasm is a part of.")
    private String collection;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="germplasmAccessionNumber", name="Accession Number", description = "The accession number of the germplasm if germplasm is being re-imported with updated synonyms/parents.")
    private String accessionNumber;

    // Removed for now, need to add to breedbase
    /*@ImportType(type=ImportFieldType.LIST, clazz=GermplasmAttribute.class)
    private List<GermplasmAttribute> germplasmAttributes;*/

    @ImportFieldType(type= ImportFieldTypeEnum.LIST, clazz = AdditionalInfo.class)
    private List<AdditionalInfo> additionalInfos;

    @ImportFieldType(type= ImportFieldTypeEnum.LIST, clazz=ExternalReference.class)
    private List<ExternalReference> externalReferences;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT, collectTime = ImportCollectTimeEnum.UPLOAD)
    @ImportMappingRequired
    @ImportFieldMetadata(id="germplasmListName", name="List Name", description = "Name of the group of germplasm being imported.")
    private String listName;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT, collectTime = ImportCollectTimeEnum.UPLOAD)
    @ImportFieldMetadata(id="germplasmListDescription", name="List Description", description = "Description of the group of germplasm being imported.")
    private String listDescription;

    public BrAPIListNewRequest constructBrAPIList(Program program, String referenceSource) {
        BrAPIListNewRequest brapiList = new BrAPIListNewRequest();
        brapiList.setListName(constructGermplasmListName(listName, program));
        brapiList.setListDescription(this.listDescription);
        brapiList.listType(BrAPIListTypes.GERMPLASM);

        // Set external references
        BrAPIExternalReference programReference = new BrAPIExternalReference();
        programReference.setReferenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS));
        programReference.setReferenceID(program.getId().toString());
        BrAPIExternalReference listReference = new BrAPIExternalReference();
        listReference.setReferenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.LISTS));
        listReference.setReferenceID(UUID.randomUUID().toString());
        brapiList.setExternalReferences(List.of(programReference, listReference));

        return brapiList;
    }

    public static String constructGermplasmListName(String listName, Program program) {
        return String.format("%s [%s-germplasm]", listName, program.getKey());
    }

    public void updateBrAPIGermplasm(BrAPIGermplasm germplasm, Program program, UUID listId, boolean commit, boolean updatePedigree) {

        if (updatePedigree) {
            if (!StringUtils.isBlank(getFemaleParentAccessionNumber())) {
                germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_GID, getFemaleParentAccessionNumber());
            }
            if (!StringUtils.isBlank(getMaleParentAccessionNumber())) {
                germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID, getMaleParentAccessionNumber());
            }
            if (!StringUtils.isBlank(getFemaleParentEntryNo())) {
                germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_ENTRY_NO, getFemaleParentEntryNo());
            }
            if (!StringUtils.isBlank(getMaleParentEntryNo())) {
                germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_ENTRY_NO, getMaleParentEntryNo());
            }
        }

        // Append synonyms to germplasm that don't already exist
        // Synonym comparison is based on name and type
        if (synonyms != null) {
            Set<BrAPIGermplasmSynonyms> existingSynonyms = new HashSet<>(germplasm.getSynonyms());
            for (String synonym: synonyms.split(";")){
                BrAPIGermplasmSynonyms brapiSynonym = new BrAPIGermplasmSynonyms();
                brapiSynonym.setSynonym(synonym);
                if (!existingSynonyms.contains(brapiSynonym)) {
                    germplasm.addSynonymsItem(brapiSynonym);
                }
            }
        }

        germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER, entryNo); //so the preview UI shows correctly

        // TODO: figure out why clear this out: brapi-server
        germplasm.setBreedingMethodDbId(null);

        if (commit) {
            setUpdateCommitFields(germplasm, program.getKey());
        }
    }


    public void setUpdateCommitFields(BrAPIGermplasm germplasm, String programKey) {

        // Set germplasm name to <Name> [<program key>-<accessionNumber>]
        String name = Utilities.appendProgramKey(germplasm.getDefaultDisplayName(), programKey, germplasm.getAccessionNumber());
        germplasm.setGermplasmName(name);

        // Update our synonyms to <Synonym> [<program key>-<accessionNumber>]
        if (germplasm.getSynonyms() != null && !germplasm.getSynonyms().isEmpty()) {
            for (BrAPIGermplasmSynonyms synonym: germplasm.getSynonyms()) {
                synonym.setSynonym(Utilities.appendProgramKey(synonym.getSynonym(), programKey, germplasm.getAccessionNumber()));
            }
        }
    }

    public boolean pedigreeExists() {
        return StringUtils.isNotBlank(getFemaleParentAccessionNumber()) ||
                StringUtils.isNotBlank(getMaleParentAccessionNumber()) ||
                StringUtils.isNotBlank(getFemaleParentEntryNo()) ||
                StringUtils.isNotBlank(getMaleParentEntryNo());
    }

    public BrAPIGermplasm constructBrAPIGermplasm(ProgramBreedingMethodEntity breedingMethod, User user, UUID listId) {
        BrAPIGermplasm germplasm = new BrAPIGermplasm();
        germplasm.setGermplasmName(getGermplasmName());
        germplasm.setDefaultDisplayName(getGermplasmName());
        germplasm.setGermplasmPUI(getGermplasmPUI());
        germplasm.setCollection(getCollection());
        germplasm.setGermplasmDbId(getAccessionNumber());
        //TODO: maybe remove germplasm import entry number
        germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_IMPORT_ENTRY_NUMBER, entryNo);
        germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_GID, getFemaleParentAccessionNumber());
        germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID, getMaleParentAccessionNumber());
        germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_ENTRY_NO, getFemaleParentEntryNo());
        germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_ENTRY_NO, getMaleParentEntryNo());
        Map<String, String> createdBy = new HashMap<>();
        createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_ID, user.getId().toString());
        createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_NAME, user.getName());
        germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_BY, createdBy);
        //TODO: Need to check that the acquisition date it in date format
        //brAPIGermplasm.setAcquisitionDate(pedigreeImport.getGermplasm().getAcquisitionDate());
        germplasm.setCountryOfOriginCode(getCountryOfOrigin());
        if (additionalInfos != null) {
            additionalInfos.stream()
                           .filter(additionalInfo -> additionalInfo.getAdditionalInfoValue() != null)
                           .forEach(additionalInfo -> germplasm.putAdditionalInfoItem(additionalInfo.getAdditionalInfoName(), additionalInfo.getAdditionalInfoValue()));
        }

        // Seed Source
        //If there is an external uid, source is associated with it as an additional external reference
        BrAPIExternalReference uidExternalReference = null;
        if (germplasmSource != null) {
            germplasm.setSeedSource(getGermplasmSource());
            if (externalUID != null) {
                uidExternalReference = new BrAPIExternalReference();
                uidExternalReference.setReferenceID(getExternalUID());
                uidExternalReference.setReferenceSource(getGermplasmSource());
            }
        }

        // External references
        germplasm.externalReferences(new ArrayList<>());
        if (externalReferences != null) {
            List<BrAPIExternalReference> brAPIExternalReferences = externalReferences.stream()
                                                                                     .map(externalReference -> externalReference.constructBrAPIExternalReference())
                                                                                     .collect(Collectors.toList());
            if (uidExternalReference != null) {
                brAPIExternalReferences.add(uidExternalReference);
            }
            germplasm.getExternalReferences().addAll(brAPIExternalReferences);
        } else if (uidExternalReference != null) {
            germplasm.getExternalReferences().add(uidExternalReference);
        }

        if (breedingMethod != null) {
            germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_BREEDING_METHOD_ID, breedingMethod.getId());
            germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_BREEDING_METHOD, breedingMethod.getName());
        }

        // Synonyms
        String blobSynonyms = getSynonyms();
        List<BrAPIGermplasmSynonyms> brapiSynonyms = new ArrayList<>();
        if (synonyms != null) {
            List<String> synonyms = Arrays.asList(blobSynonyms.split(";")).stream()
                                          .map(synonym -> synonym.strip())
                                          .distinct()
                                          .collect(Collectors.toList());
            // Create synonym
            for (String synonym: synonyms) {
                BrAPIGermplasmSynonyms brapiSynonym = new BrAPIGermplasmSynonyms();
                brapiSynonym.setSynonym(synonym);
                brapiSynonyms.add(brapiSynonym);
            }
            germplasm.setSynonyms(brapiSynonyms);
        }

        return germplasm;
    }

    private void setBrAPIGermplasmCommitFields(BrAPIGermplasm germplasm, String programKey, String referenceSource, Supplier<BigInteger> nextVal) {

        // Add UUID external reference
        UUID newUUID = UUID.randomUUID();
        BrAPIExternalReference newReference = new BrAPIExternalReference();
        newReference.setReferenceSource(referenceSource);
        newReference.setReferenceID(newUUID.toString());
        germplasm.getExternalReferences().add(newReference);

        // Get the next accession number
        germplasm.setAccessionNumber(nextVal.get().toString());

        // Set germplasm name to <Name> [<program key>-<accessionNumber>]
        germplasm.setGermplasmName(Utilities.appendProgramKey(germplasm.getDefaultDisplayName(), programKey, germplasm.getAccessionNumber()));

        // Set createdDate field
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        germplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_DATE, formatter.format(now));

        // Update our synonyms to <Synonym> [<program key>-<accessionNumber>]
        if (germplasm.getSynonyms() != null && !germplasm.getSynonyms().isEmpty()) {
            for (BrAPIGermplasmSynonyms synonym: germplasm.getSynonyms()) {
                synonym.setSynonym(Utilities.appendProgramKey(synonym.getSynonym(), programKey, germplasm.getAccessionNumber()));
            }
        }
    }

    public BrAPIGermplasm constructBrAPIGermplasm(Program program, ProgramBreedingMethodEntity breedingMethod, User user, boolean commit, String referenceSource, Supplier<BigInteger> nextVal, UUID listId) {
        BrAPIGermplasm germplasm = constructBrAPIGermplasm(breedingMethod, user, listId);
        if (commit) {
            setBrAPIGermplasmCommitFields(germplasm, program.getKey(), referenceSource, nextVal);
        }
        germplasm.setCommonCropName(program.getBrapiProgram().getCommonCropName());

        // Set program id in external references
        BrAPIExternalReference newReference = new BrAPIExternalReference();
        newReference.setReferenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS));
        newReference.setReferenceID(program.getId().toString());
        germplasm.getExternalReferences().add(newReference);

        return germplasm;
    }
}
