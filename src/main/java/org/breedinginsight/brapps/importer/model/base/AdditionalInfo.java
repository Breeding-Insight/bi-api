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
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportType;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="AdditionalInfo", name="Additional Information",
        description = "This is used for information that doesn't exist in the provided fields, but you still want to be tracked with this object.")
public class AdditionalInfo implements BrAPIObject {

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="additionalInfoName", name="Additional Information Name",
            description = "The name of the property for the additional information.")
    private String additionalInfoName;

    @ImportType(type=ImportFieldType.TEXT)
    @ImportFieldMetadata(id="additionalInfoValue", name="Additional Information Value",
            description = "The value of the property for the additional information.")
    private String additionalInfoValue;
}
