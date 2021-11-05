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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @ImportFieldMetadata(id="maleParentDBID", name="Male Parent DBID", description = "The DBID of the male parent of the germplasm. Can be left blank for self crosses.")
    private String maleParentDBID;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="femaleParentEntryNo", name="Female Parent Entry Number", description = "The entry number of the female parent of the germplasm. Used to import offspring with progenitors not yet in the database.")
    private String femaleParentEntryNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="maleParentEntryNo", name="Male Parent Entry Number", description = "The entry number of the male parent of the germplasm. Used to import offspring with progenitors not yet in the database. Can be left blank for self crosses.")
    private String maleParentEntryNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="germplasmPUI", name="Germplasm Permanent Unique Identifier", description = "The Permanent Unique Identifier which represents a germplasm from the source or donor.")
    private String germplasmPUI;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="accessionNumber", name="Accession Number", description = "This is the unique identifier for accessions within a genebank, and is assigned when a sample is entered into the genebank collection.")
    private String accessionNumber;

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

    public BrAPIGermplasm constructBrAPIGermplasm() {
        BrAPIGermplasm germplasm = new BrAPIGermplasm();
        germplasm.setGermplasmName(getGermplasmName()); //TODO: will be modified in later card
        germplasm.setDefaultDisplayName(getGermplasmName());
        germplasm.setGermplasmPUI(getGermplasmPUI());
        germplasm.setAccessionNumber(getAccessionNumber());
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
            germplasm.putAdditionalInfoItem("Import Entry Number", entryNo);
        }

        //If there is an external uid, source is associated with it as an additional external reference
        BrAPIExternalReference UIDExternalReference = null;
        if (germplasmSource != null) {
            if (externalUID != null) {
                UIDExternalReference = new BrAPIExternalReference();
                UIDExternalReference.setReferenceID(getExternalUID());
                UIDExternalReference.setReferenceSource(getGermplasmSource());
            } else {
                germplasm.setSeedSourceDescription(getGermplasmSource());
            }
        }

        if (externalReferences != null) {
            List<BrAPIExternalReference> brAPIExternalReferences = externalReferences.stream()
                    .map(externalReference -> externalReference.constructBrAPIExternalReference())
                    .collect(Collectors.toList());
            if (UIDExternalReference != null) {
                brAPIExternalReferences.add(UIDExternalReference);
            }
            germplasm.setExternalReferences(brAPIExternalReferences);
        } else if (UIDExternalReference != null) {
            germplasm.setExternalReferences(Collections.singletonList(UIDExternalReference));
        }

        return germplasm;
    }

    public BrAPIGermplasm constructBrAPIGermplasm(BrAPIProgram brAPIProgram) {
        BrAPIGermplasm germplasm = constructBrAPIGermplasm();
        germplasm.setCommonCropName(brAPIProgram.getCommonCropName());

        // Set programId in additionalInfo
        germplasm.putAdditionalInfoItem("programId", brAPIProgram.getProgramDbId());

        return germplasm;
    }

}