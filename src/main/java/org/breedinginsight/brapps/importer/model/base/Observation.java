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
import lombok.Setter;
import org.breedinginsight.brapps.importer.model.config.*;

@Getter
@Setter
@ImportFieldMetadata(id="Observation", name="Observation",
        description = "An observation object is data that is collected on a trait for a given object being observed.")
public class Observation implements BrAPIObject {

    @ImportType(type= ImportFieldType.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={"studyDbId", "studyName"})
    })
    @ImportFieldMetadata(id="study", name="Study",
            description = "Study that the observation belongs to.")
    private ImportRelation study;

    @ImportType(type= ImportFieldType.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={"observationUnitDbId", "observationName"})
    })
    @ImportFieldMetadata(id="observationUnit", name="Observation Unit",
            description = "Observation unit that the observation is taken on.")
    private ImportRelation observationUnit;

    @ImportType(type= ImportFieldType.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP_CONSTANT_VALUE, importFields={"traitId", "traitName"})
    })
    @ImportFieldMetadata(id="trait", name="Trait",
            description = "Trait that the observation is recording.")
    private ImportRelation trait;

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="value", name="Observation Value", description = "Value of the observation.")
    private String value;

    @ImportType(type= ImportFieldType.DATE)
    @ImportFieldMetadata(id="observationDate", name="Observation Date", description = "Date that the observation was taken.")
    private String observationDate;
}
