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

package org.breedinginsight.brapps.importer.model.imports.germplasm;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.breedinginsight.brapps.importer.model.base.Germplasm;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;

@Getter
@Setter
@NoArgsConstructor
@ImportConfigMetadata(id="GermplasmImport", name="Germplasm Import",
        description = "This import is used to create germplasm and create a pedigree by specifying parental connections.")
public class GermplasmImport implements BrAPIImport {
    @ImportFieldType(type = ImportFieldTypeEnum.OBJECT)
    @ImportMappingRequired
    private Germplasm germplasm;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT, collectTime=ImportCollectTimeEnum.UPLOAD)
    @ImportMappingRequired
    @ImportFieldMetadata(id="germplasmListName", name="Import Group Name", description = "Name of the group of germplasm being imported.")
    private String listName;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT, collectTime=ImportCollectTimeEnum.UPLOAD)
    @ImportFieldMetadata(id="germplasmListDescription", name="Import Group Description", description = "Name of the group of germplasm being imported.")
    private String listDescription;
}
