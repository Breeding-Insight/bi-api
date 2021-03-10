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
import org.breedinginsight.brapps.importer.model.config.*;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="Cross", name="Cross",
        description = "A cross connects two germplasm objects to form a pedigree.")
public class Cross implements BrAPIObject {

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="crossName", name="Cross Name",
            description = "Name of the cross.")
    private String crossName;

    @ImportType(type= ImportFieldType.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.FILE_LOOKUP),
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={"germplasmDbId"})
    })
    @ImportFieldMetadata(id="femaleParent", name="Female Parent",
            description = "Name of the cross.")
    private ImportRelation femaleParent;
}
