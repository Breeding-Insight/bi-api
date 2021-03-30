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
import org.brapi.v2.model.germ.BrAPICrossCrossAttributes;
import org.breedinginsight.brapps.importer.model.config.ImportFieldMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldRequired;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportType;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="CrossAttribute", name="Cross Attribute",
        description = "A properties of the cross, or cross conditions.")
public class CrossAttribute implements BrAPIObject {
    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldRequired
    @ImportFieldMetadata(id="crossAttributeName", name="Cross Attribute Name", description = "The name of the cross attribute.")
    private String crossAttributeName;

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldRequired
    @ImportFieldMetadata(id="crossAttributeValue", name="Cross Attribute Value", description = "The value for the cross attribute for the given cross.")
    private String crossAttributeValue;

    public BrAPICrossCrossAttributes constructCrossAttribute() {
        BrAPICrossCrossAttributes crossAttribute = new BrAPICrossCrossAttributes();
        crossAttribute.setCrossAttributeName(getCrossAttributeName());
        crossAttribute.setCrossAttributeValue(getCrossAttributeValue());
        return crossAttribute;
    }
}
