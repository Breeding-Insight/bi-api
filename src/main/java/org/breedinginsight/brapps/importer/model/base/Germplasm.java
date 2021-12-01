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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.model.Program;
import org.jooq.DSLContext;

import java.math.BigInteger;
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
    @ImportFieldMetadata(id="breedingMethod", name="Breeding Method", description = "The breeding method name or code")
    private String breedingMethod;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="germplasmSource", name="Source", description = "The germplasm origin. If External UID present, assumed to be the source associated with the External UID.")
    private String germplasmSource;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="externalUID", name="External UID", description = "External UID")
    private String externalUID;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="entryNo", name="Entry No.", description = "The order of germplasm in the import list ( 1,2,3,….n). If no entry  number is specified in the import germplasm list, the database will assign entry number upon import.")
    private String entryNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="femaleParentDBID", name="Female Parent DBID", description = "The DBID of the female parent of the germplasm.")
    private String femaleParentDBID;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="maleParentDBID", name="Male Parent DBID", description = "The DBID of the male parent of the germplasm.")
    private String maleParentDBID;

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

    // Removed for now, need to add to breedbase
    /*@ImportType(type=ImportFieldType.LIST, clazz=GermplasmAttribute.class)
    private List<GermplasmAttribute> germplasmAttributes;*/

    @ImportFieldType(type= ImportFieldTypeEnum.LIST, clazz = AdditionalInfo.class)
    private List<AdditionalInfo> additionalInfos;

    @ImportFieldType(type= ImportFieldTypeEnum.LIST, clazz=ExternalReference.class)
    private List<ExternalReference> externalReferences;

    public BrAPIGermplasm constructBrAPIGermplasm(BreedingMethodEntity breedingMethod) {

        // TODO: Add createdBy -> userId and createdBy -> userName to additional info
        // TODO: Add createdDate to additional info
        // TODO: Add breedingMethodId to additional info
        BrAPIGermplasm germplasm = new BrAPIGermplasm();
        germplasm.setGermplasmName(getGermplasmName());
        germplasm.setDefaultDisplayName(getGermplasmName());
        germplasm.setGermplasmPUI(getGermplasmPUI());
        germplasm.setCollection(getCollection());
        //TODO: Need to check that the acquisition date it in date format
        //brAPIGermplasm.setAcquisitionDate(pedigreeImport.getGermplasm().getAcquisitionDate());
        germplasm.setCountryOfOriginCode(getCountryOfOrigin());
        if (additionalInfos != null) {
            additionalInfos.stream()
                    .filter(additionalInfo -> additionalInfo.getAdditionalInfoValue() != null)
                    .forEach(additionalInfo -> germplasm.putAdditionalInfoItem(additionalInfo.getAdditionalInfoName(), additionalInfo.getAdditionalInfoValue()));
        }

        //TODO: add logic later for generating entry numbers if not provided by user
        if (entryNo != null){
            germplasm.putAdditionalInfoItem("importEntryNumber", entryNo);
        }

        // Seed Source
        //If there is an external uid, source is associated with it as an additional external reference
        BrAPIExternalReference uidExternalReference = null;
        if (germplasmSource != null) {
            if (externalUID != null) {
                uidExternalReference = new BrAPIExternalReference();
                uidExternalReference.setReferenceID(getExternalUID());
                uidExternalReference.setReferenceSource(getGermplasmSource());
            } else {
                germplasm.setSeedSourceDescription(getGermplasmSource());
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
            germplasm.putAdditionalInfoItem("breedingMethodId", breedingMethod.getId());
            germplasm.putAdditionalInfoItem("breedingMethod", breedingMethod.getName());
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
        germplasm.setGermplasmName(String.format("%s [%s-%s]", germplasm.getDefaultDisplayName(), programKey, germplasm.getAccessionNumber()));
    }

    public BrAPIGermplasm constructBrAPIGermplasm(Program program, BreedingMethodEntity breedingMethod, boolean commit, String referenceSource, Supplier<BigInteger> nextVal) {
        BrAPIGermplasm germplasm = constructBrAPIGermplasm(breedingMethod);
        if (commit) {
            setBrAPIGermplasmCommitFields(germplasm, program.getKey(), referenceSource, nextVal);
        }
        germplasm.setCommonCropName(program.getBrapiProgram().getCommonCropName());

        // Set program id in external references
        BrAPIExternalReference newReference = new BrAPIExternalReference();
        newReference.setReferenceSource(String.format("%s/programs", referenceSource));
        newReference.setReferenceID(program.getId().toString());
        germplasm.getExternalReferences().add(newReference);

        return germplasm;
    }

}