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
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel;
import org.breedinginsight.brapps.importer.model.config.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="ObservationUnit", name="Observation Unit",
        description = "An observation unit is the physical representation of a breeding unit. This is the unit that an observation is being made on. Example: Plant, Plot")
public class ObservationUnit implements BrAPIObject {

    private static final String LOCATION_NAME = "locationName";
    private static final String TRIAL_NAME = "trialName";
    private static final String STUDY_NAME = "studyName";
    private static final String GERMPLASM_NAME = "germplasmName";

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="observationUnitName", name="Observation Unit Name",
            description = "The name of the observation unit.")
    @ImportMappingRequired
    private String observationUnitName;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="observationLevel", name="Observation Level",
            description = "The type of the observation unit. Example: Plot, Plant.")
    @ImportMappingRequired
    private String observationLevel;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="observationUnitPermanentID", name="Observation Permanent ID",
            description = "This is used to identify observation units between studies as the same physical object. For example, a perennial plant may be part of multiple studies, and have an observation unit for each of those studies, but will be traceable by this id.")
    //@ImportMappingRequired
    private String observationUnitPermanentID;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations = {
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields = {"observationUnitName"})
    })
    @ImportFieldMetadata(id="observationUnitParent", name="Parent Observation Unit", description = "The observation unit that contains this observation unit.")
    private MappedImportRelation observationUnitParent;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations = {
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields = {LOCATION_NAME})
    })
    @ImportFieldMetadata(id="location", name="Location", description = "The location the observation unit is in.")
    private MappedImportRelation location;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations = {
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields = {STUDY_NAME})
    })
    @ImportFieldMetadata(id="study", name="Study", description = "The study the observation unit is in.")
    @ImportMappingRequired
    private MappedImportRelation study;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations = {
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields = {GERMPLASM_NAME})
    })
    @ImportFieldMetadata(id="germplasm", name="Germplasm", description = "The germplasm that this observation unit represents.")
    private MappedImportRelation germplasm;

    @ImportFieldType(type= ImportFieldTypeEnum.LIST, clazz = AdditionalInfo.class)
    private List<AdditionalInfo> additionalInfos;

    @ImportFieldType(type= ImportFieldTypeEnum.LIST, clazz=ExternalReference.class)
    private List<ExternalReference> externalReferences;

    public BrAPIObservationUnit constructBrAPIObservationUnit() {

        BrAPIObservationUnit observationUnit = new BrAPIObservationUnit();
        observationUnit.setObservationUnitName(getObservationUnitName());

        BrAPIObservationUnitHierarchyLevel level = new BrAPIObservationUnitHierarchyLevel();
        level.setLevelName(getObservationLevel());

        if (getStudy().getTargetColumn().equals(STUDY_NAME)) {
            observationUnit.setStudyName(getStudy().getReferenceValue());
        }

        if (getGermplasm().getTargetColumn().equals(GERMPLASM_NAME)) {
            observationUnit.setGermplasmName(getGermplasm().getReferenceValue());
        }

        /*
        if (getLocation().getTargetColumn().equals(LOCATION_NAME)) {
            observationUnit.setLocationName(getLocation().getReferenceValue());
        }
         */

        List<BrAPIExternalReference> brAPIexternalReferences = new ArrayList<>();
        //TODO: Should we be checking this back here, or depending on the user to set it properly?
        //BrAPIExternalReference brAPIExternalReference = new BrAPIExternalReference();
        //brAPIExternalReference.setReferenceSource(BrAPIObservationUnitDAO.OU_ID_REFERENCE_SOURCE);
        //brAPIExternalReference.setReferenceID(getObservationUnitPermanentID());
        //brAPIexternalReferences.add(brAPIExternalReference);

        if (observationUnit.getExternalReferences() != null){
            getExternalReferences().forEach(externalReference -> brAPIexternalReferences.add(externalReference.constructBrAPIExternalReference()));
        }
        observationUnit.setExternalReferences(brAPIexternalReferences);

        if (additionalInfos != null) {
            Map<String, String> brAPIAdditionalInfos = additionalInfos.stream()
                    .collect(Collectors.toMap(AdditionalInfo::getAdditionalInfoName, AdditionalInfo::getAdditionalInfoValue));
            observationUnit.setAdditionalInfo(brAPIAdditionalInfos);
        }

        return observationUnit;
    }

    public BrAPIObservationUnit constructBrAPIObservationUnit(String germplasmDbId) {

        BrAPIObservationUnit observationUnit = constructBrAPIObservationUnit();
        observationUnit.setGermplasmDbId(germplasmDbId);
        return constructBrAPIObservationUnit();
    }

}
