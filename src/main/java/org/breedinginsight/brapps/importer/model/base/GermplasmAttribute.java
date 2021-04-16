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
import org.breedinginsight.brapps.importer.model.config.ImportMappingRequired;
import org.breedinginsight.brapps.importer.model.config.ImportFieldTypeEnum;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="GermplasmAttribute", name="Germplasm Attribute",
        description = "A miscellaneous attribute that is attached to the germplasm. This can be something like strain, disease resistance ratings, etc.")
public class GermplasmAttribute implements BrAPIObject {

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="germplasmAttributeName", name="Germplasm Attribute Name", description = "The name of the germplasm attribute.")
    private String attributeName;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="germplasmAttributeDescription", name="Germplasm Attribute Description", description = "The description of the germplasm attribute.")
    private String attributeDescription;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="germplasmAttributeValue", name="Germplasm Attribute Value", description = "The value of the germplasm attribute.")
    private String attributeValue;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="germplasmAttributeCategory", name="Germplasm Attribute Category", description = "The category of the germplasm attribute. Example: Morphological.")
    private String attributeCategory;


}
