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
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.config.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@ImportFieldMetadata(id="Observation", name="Observation",
        description = "An observation object is data that is collected on a trait for a given object being observed.")
public class Observation implements BrAPIObject {

    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final String TRAIT_NAME = "traitName";
    private static final String OBSERVATION_UNIT_NAME = "observationUnitName";
    private static final String STUDY_NAME = "studyName";

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={STUDY_NAME})
    })
    @ImportFieldMetadata(id="study", name="Study",
            description = "Study that the observation belongs to.")
    private MappedImportRelation study;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={OBSERVATION_UNIT_NAME})
    })
    @ImportFieldMetadata(id="observationUnit", name="Observation Unit",
            description = "Observation unit that the observation is taken on.")
    private MappedImportRelation observationUnit;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={TRAIT_NAME}),
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP_CONSTANT_VALUE, importFields={TRAIT_NAME})
    })
    @ImportFieldMetadata(id="trait", name="Trait",
            description = "Trait that the observation is recording.")
    private MappedImportRelation trait;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="value", name="Observation Value", description = "Value of the observation.")
    private String value;

    @ImportFieldType(type= ImportFieldTypeEnum.DATE)
    @ImportFieldMetadata(id="observationDate", name="Observation Date", description = "Date that the observation was taken.")
    private String observationDate;

    @ImportFieldType(type= ImportFieldTypeEnum.LIST, clazz = AdditionalInfo.class)
    private List<AdditionalInfo> additionalInfos;

    public BrAPIObservation constructBrAPIObservation() {
        BrAPIObservation observation = new BrAPIObservation();
        observation.setValue(getValue());

        if (getTrait().getTargetColumn().equals(TRAIT_NAME)) {
            observation.setObservationVariableName(getTrait().getReferenceValue());
        }

        if (getObservationUnit().getTargetColumn().equals(OBSERVATION_UNIT_NAME)) {
            observation.setObservationUnitName(getObservationUnit().getReferenceValue());
        }

        if (getStudy().getTargetColumn().equals(STUDY_NAME)) {
            // don't have name field so store in DbId and lookup or require a DbId in file?
            observation.setStudyDbId(getStudy().getReferenceValue());
        }

        // TODO: use common time format, using discrete analyzer format for now 16/12/2020 16:16:49
        LocalDateTime datetime = LocalDateTime.parse(getObservationDate(), formatter);
        ZonedDateTime zoned = datetime.atZone(ZoneId.of("UTC"));
        OffsetDateTime timestamp = zoned.toOffsetDateTime();
        observation.setObservationTimeStamp(timestamp);

        if (additionalInfos != null) {
            Map<String, String> brAPIAdditionalInfos = additionalInfos.stream()
                    .filter(additionalInfo -> additionalInfo.getAdditionalInfoValue() != null)
                    .collect(Collectors.toMap(AdditionalInfo::getAdditionalInfoName, AdditionalInfo::getAdditionalInfoValue));
            observation.setAdditionalInfo(brAPIAdditionalInfos);
        }

        return observation;
    }

}
