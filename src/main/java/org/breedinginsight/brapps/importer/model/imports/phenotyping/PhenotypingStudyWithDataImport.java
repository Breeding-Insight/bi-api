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
package org.breedinginsight.brapps.importer.model.imports.phenotyping;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.breedinginsight.brapps.importer.model.base.*;
import org.breedinginsight.brapps.importer.model.config.ImportConfigMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportFieldTypeEnum;
import org.breedinginsight.brapps.importer.model.config.ImportMappingRequired;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ImportConfigMetadata(id="PhenotypingStudyWithDataImport", name="Phenotyping Study With Data",
        description = "This import is used to create a phenotyping study including germplasm & observations.")
public class PhenotypingStudyWithDataImport implements BrAPIImport {

    @ImportFieldType(type = ImportFieldTypeEnum.OBJECT)
    @ImportMappingRequired
    private Germplasm germplasm;

    @ImportFieldType(type = ImportFieldTypeEnum.OBJECT)
    @ImportMappingRequired
    private Trial trial;

    @ImportFieldType(type = ImportFieldTypeEnum.OBJECT)
    @ImportMappingRequired
    private Location location;

    @ImportFieldType(type = ImportFieldTypeEnum.OBJECT)
    @ImportMappingRequired
    private Study study;

    @ImportFieldType(type = ImportFieldTypeEnum.OBJECT)
    @ImportMappingRequired
    private ObservationUnit observationUnit;

    @ImportFieldType(type= ImportFieldTypeEnum.LIST, clazz = Observation.class)
    private List<Observation> observations;

}