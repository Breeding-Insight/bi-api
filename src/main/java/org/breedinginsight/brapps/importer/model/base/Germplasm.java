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
import org.breedinginsight.brapps.importer.model.config.ImportFieldMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldRequired;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportType;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="Germplasm", name="Germplasm",
        description = "A germplasm object corresponds to a non-physical entity and is used to track a unique genetic composition. This is commonly used for populations.")
public class Germplasm implements BrAPIObject {

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldRequired
    @ImportFieldMetadata(id="germplasmName", name="Germplasm Name", description = "Name of germplasm")
    private String germplasmName;

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="germplasmPUI", name="Germplasm Permanent Unique Identifier", description = "The Permanent Unique Identifier which represents a germplasm from the source or donor.")
    private String germplasmPUI;

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldRequired
    @ImportFieldMetadata(id="commonCropName", name="Species Name", description = "Name of the species")
    private String species;

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="accessionNumber", name="Accession Number", description = "This is the unique identifier for accessions within a genebank, and is assigned when a sample is entered into the genebank collection.")
    private String accessionNumber;

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="acquisitionDate", name="Acquisition Date", description = "The date this germplasm was acquired by the genebank.")
    private String acquisitionDate;

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="countryOfOrigin", name="Country of Origin", description = "Two letter code for the country of origin.")
    private String countryOfOrigin;

    // Removed for now, need to add to breedbase
    /*@ImportType(type=ImportFieldType.LIST, clazz=GermplasmAttribute.class)
    private List<GermplasmAttribute> germplasmAttributes;*/

    @ImportType(type= ImportFieldType.LIST, clazz = AdditionalInfo.class)
    private List<AdditionalInfo> additionalInfos;

    @ImportType(type=ImportFieldType.LIST, clazz=ExternalReference.class)
    private List<ExternalReference> externalReferences;

}