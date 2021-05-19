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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.model.config.ImportFieldMetadata;
import org.breedinginsight.brapps.importer.model.config.ImportFieldType;
import org.breedinginsight.brapps.importer.model.config.ImportFieldTypeEnum;
import org.breedinginsight.brapps.importer.model.config.ImportMappingRequired;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ImportFieldMetadata(id="Study", name="Study",
        description = "A study.")
public class Study implements BrAPIObject {
    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="studyName", name="Study Name", description = "The name of the study.")
    private String studyName;

    public BrAPIStudy constructBrAPIStudy() {
        BrAPIStudy study = new BrAPIStudy();
        study.setStudyName(getStudyName());
        study.setActive(true);
        return study;
    }

    public Study constructStudy() {
        Study study = new Study();
        study.setStudyName(getStudyName());
        return study;
    }
}