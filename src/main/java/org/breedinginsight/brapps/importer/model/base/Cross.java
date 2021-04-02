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
import org.brapi.v2.model.germ.BrAPICross;
import org.brapi.v2.model.germ.BrAPICrossCrossAttributes;
import org.brapi.v2.model.germ.BrAPICrossType;
import org.breedinginsight.brapps.importer.model.config.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="Cross", name="Cross",
        description = "A cross connects two germplasm objects to form a pedigree.")
public class Cross implements BrAPIObject {

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="crossName", name="Cross Name", description = "Name of the cross. Defaults to parent germplasm names if not provided.")
    private String crossName;

    @ImportType(type= ImportFieldType.TEXT)
    @ImportFieldMetadata(id="crossType", name="Cross Type", description = "Type of cross. Example: BIPARENTAL.")
    private String crossType;

    @ImportType(type= ImportFieldType.RELATIONSHIP)
    @ImportFieldRelations(relations = {
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields = {"germplasmName", "observationUnitName"}),
    })
    @ImportFieldRequired
    @ImportFieldMetadata(id="femaleParent", name="Female Parent", description = "The female parent of the germplasm.")
    private ImportRelation femaleParent;

    @ImportType(type= ImportFieldType.RELATIONSHIP)
    @ImportFieldRelations(relations = {
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields = {"germplasmName", "observationUnitName"}),
    })
    @ImportFieldMetadata(id="maleParent", name="Male Parent", description = "The male parent of the germplasm. Can be left blank for self crosses.")
    private ImportRelation maleParent;

    @ImportType(type= ImportFieldType.DATE)
    @ImportFieldMetadata(id="crossDateTime", name="Cross Date Time", description = "The date-time that the cross took place.")
    private String crossDateTime;

    @ImportType(type= ImportFieldType.LIST, clazz = CrossAttribute.class)
    private List<CrossAttribute> crossAttributes;

    @ImportType(type=ImportFieldType.LIST, clazz=ExternalReference.class)
    @ImportFieldMetadata(id="externalReferences", name="External References",
            description = "External references to track external IDs.")
    private List<ExternalReference> externalReferences;

    public BrAPICross getBrAPICross() {
        BrAPICross cross = new BrAPICross();
        //TODO: Check proper date format
        //cross.setCrossDateTime(cross.getCrossDateTime());
        cross.setCrossName(getCrossName());
        //TODO: Check that value is legit
        BrAPICrossType brAPICrossType = BrAPICrossType.valueOf(getCrossType().toUpperCase());
        cross.setCrossType(brAPICrossType);

        if (cross.getCrossAttributes() != null) {
            cross.setCrossAttributes(crossAttributes.stream()
                .map(crossAttribute -> crossAttribute.constructCrossAttribute())
                    .collect(Collectors.toList())
            );
        }

        if (externalReferences != null) {
            List<BrAPIExternalReference> brAPIExternalReferences = externalReferences.stream()
                    .map(externalReference -> externalReference.constructBrAPIExternalReference())
                    .collect(Collectors.toList());
            cross.setExternalReferences(brAPIExternalReferences);
        }

        return cross;
    }

}
