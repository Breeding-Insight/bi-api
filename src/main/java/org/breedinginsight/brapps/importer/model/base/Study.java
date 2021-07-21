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
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPIStudyExperimentalDesign;
import org.breedinginsight.brapps.importer.model.config.*;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="Study", name="Study",
        description = "A study.")
public class Study implements BrAPIObject {

    private static final String LOCATION_NAME = "locationName";
    private static final String TRIAL_NAME = "trialName";

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="studyName", name="Study Name", description = "The name of the study.")
    private String studyName;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportMappingRequired
    @ImportFieldMetadata(id="experimentalDesignPUI", name="Experimental Design PUI",
            description = "Type of experimental design, must be one of the following: " +
                    "CRD, Alpha, MAD, Lattice, Augmented, RCBD, p-rep, splitplot, greenhouse, Westcott, Analysis")
    private String experimentalDesignPUI;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportMappingRequired
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={LOCATION_NAME})
    })
    @ImportFieldMetadata(id="location", name="Location",
            description = "Location that the study is at.")
    private MappedImportRelation location;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportMappingRequired
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={TRIAL_NAME})
    })
    @ImportFieldMetadata(id="trial", name="Trial",
            description = "Trial that the study is a part of.")
    private MappedImportRelation trial;


    public BrAPIStudy constructBrAPIStudy() {
        BrAPIStudy study = new BrAPIStudy();
        study.setStudyName(getStudyName());
        study.setActive(true);

        BrAPIStudyExperimentalDesign design = new BrAPIStudyExperimentalDesign();
        design.setPUI(getExperimentalDesignPUI());
        study.setExperimentalDesign(design);

        if (getLocation().getTargetColumn().equals(LOCATION_NAME)) {
            study.setLocationName(getLocation().getReferenceValue());
        }

        if (getTrial().getTargetColumn().equals(TRIAL_NAME)) {
            study.setTrialName(getTrial().getReferenceValue());
        }

        return study;
    }

}