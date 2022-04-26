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

package org.breedinginsight.brapps.importer.imports.germplasm.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.base.model.config.*;
import org.breedinginsight.brapps.importer.base.model.config.BrAPIObject;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="Germplasm", name="Germplasm",
        description = "A germplasm object corresponds to a non-physical entity and is used to track a unique genetic composition. This is commonly used for populations.")
public class Germplasm implements BrAPIObject {

    public static final String GERMPLASM_NAME_TARGET = "germplasmName";

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="germplasmName", name="Name", description = "Name of germplasm")
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
    @ImportFieldMetadata(id="femaleParentGID", name="Female Parent GID", description = "The accession number (GID) of the female parent of the germplasm.")
    private String femaleParentDBID;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="maleParentGID", name="Male Parent GID", description = "The accession number (GID) of the male parent of the germplasm.")
    private String maleParentDBID;

    @ImportFieldType(type= ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id="entryNo", name="Entry No", description = "The order of germplasm in the import list ( 1,2,3,….n). If no entry  number is specified in the import germplasm list, the database will assign entry number upon import.")
    private String entryNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="femaleParentEntryNo", name="Female Parent Entry No", description = "The entry number of the female parent of the germplasm. Used to import offspring with progenitors not yet in the database.")
    private String femaleParentEntryNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="maleParentEntryNo", name="Male Parent Entry No", description = "The entry number of the male parent of the germplasm. Used to import offspring with progenitors not yet in the database.")
    private String maleParentEntryNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="externalUID", name="External UID", description = "External UID")
    private String externalUID;

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
        // Set external reference
        BrAPIExternalReference reference = new BrAPIExternalReference();
        reference.setReferenceSource(String.format("%s/programs", referenceSource));
        reference.setReferenceID(program.getId().toString());
        brapiList.setExternalReferences(List.of(reference));
        return brapiList;
    }

    public static String constructGermplasmListName(String listName, Program program) {
        return String.format("%s [%s-germplasm]", listName, program.getKey());
    }

    public BrAPIGermplasm constructBrAPIGermplasm(BreedingMethodEntity breedingMethod, User user) {
        BrAPIGermplasm germplasm = new BrAPIGermplasm();
        germplasm.setGermplasmName(getGermplasmName());
        germplasm.setDefaultDisplayName(getGermplasmName());
        germplasm.putAdditionalInfoItem("importEntryNumber", entryNo);
        germplasm.putAdditionalInfoItem("femaleParentGid", getFemaleParentDBID());
        germplasm.putAdditionalInfoItem("maleParentGid", getMaleParentDBID());
        germplasm.putAdditionalInfoItem("femaleParentEntryNo", getFemaleParentEntryNo());
        germplasm.putAdditionalInfoItem("maleParentEntryNo", getMaleParentEntryNo());
        Map<String, String> createdBy = new HashMap<>();
        createdBy.put("userId", user.getId().toString());
        createdBy.put("userName", user.getName());
        germplasm.putAdditionalInfoItem("createdBy", createdBy);

        // Seed Source
        //If there is an external uid, source is associated with it as an additional external reference
        germplasm.externalReferences(new ArrayList<>());
        if (germplasmSource != null) {
            germplasm.setSeedSource(getGermplasmSource());
            if (externalUID != null) {
                BrAPIExternalReference uidExternalReference = new BrAPIExternalReference();
                uidExternalReference.setReferenceID(getExternalUID());
                uidExternalReference.setReferenceSource(getGermplasmSource());
                germplasm.getExternalReferences().add(uidExternalReference);
            }
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
        germplasm.setGermplasmName(Utilities.appendProgramKey(germplasm.getDefaultDisplayName(), programKey, germplasm.getAccessionNumber()));

        // Set createdDate field
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        germplasm.putAdditionalInfoItem("createdDate", formatter.format(now));
    }

    public BrAPIGermplasm constructBrAPIGermplasm(Program program, BreedingMethodEntity breedingMethod, User user, boolean commit, String referenceSource, Supplier<BigInteger> nextVal) {
        BrAPIGermplasm germplasm = constructBrAPIGermplasm(breedingMethod, user);
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