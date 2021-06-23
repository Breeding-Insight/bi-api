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
import lombok.Setter;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapps.importer.model.config.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ImportFieldMetadata(id="Observation", name="Observation",
        description = "An observation object is data that is collected on a trait for a given object being observed.")
public class Observation implements BrAPIObject {

    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final String TRAIT_NAME = "traitName";
    private static final String OBSERVATION_UNIT_NAME = "observationUnitName";
    private static final String STUDY_NAME = "studyName";
    private static final String GERMPLASM_NAME = "germplasmName";

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={STUDY_NAME})
    })
    @ImportFieldMetadata(id="study", name="Study",
            description = "Study that the observation belongs to.")
    //@EqualsAndHashCode.Include
    private MappedImportRelation study;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={GERMPLASM_NAME})
    })
    @ImportFieldMetadata(id="germplasm", name="Germplasm",
            description = "Germplasm that the observation belongs to.")
    @EqualsAndHashCode.Include
    private MappedImportRelation germplasm;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={OBSERVATION_UNIT_NAME})
    })
    @ImportFieldMetadata(id="observationUnit", name="Observation Unit",
            description = "Observation unit that the observation is taken on.")
    @EqualsAndHashCode.Include
    private MappedImportRelation observationUnit;

    @ImportFieldType(type= ImportFieldTypeEnum.RELATIONSHIP)
    @ImportFieldRelations(relations={
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP, importFields={TRAIT_NAME}),
            @ImportFieldRelation(type = ImportRelationType.DB_LOOKUP_CONSTANT_VALUE, importFields={TRAIT_NAME})
    })
    @ImportFieldMetadata(id="trait", name="Trait",
            description = "Trait that the observation is recording.")
    @EqualsAndHashCode.Include
    private MappedImportRelation trait;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="value", name="Observation Value", description = "Value of the observation.")
    private String value;

    @ImportFieldType(type= ImportFieldTypeEnum.DATE)
    @ImportFieldMetadata(id="observationDate", name="Observation Date", description = "Date that the observation was taken.")
    @EqualsAndHashCode.Include
    private String observationDate;

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

        if (getGermplasm().getTargetColumn().equals(GERMPLASM_NAME)) {
            observation.setGermplasmName(getGermplasm().getReferenceValue());
        }


        // TODO: use common time format, using discrete analyzer format for now 16/12/2020 16:16:49
        LocalDateTime datetime = LocalDateTime.parse(getObservationDate(), formatter);
        ZonedDateTime zoned = datetime.atZone(ZoneId.of("UTC"));
        OffsetDateTime timestamp = zoned.toOffsetDateTime();
        observation.setObservationTimeStamp(timestamp);

        return observation;
    }

    public static Observation observationFromBrapiObservation(BrAPIObservation brapiObservation) {
        Observation observation = new Observation();
        // TODO: figure out how to handle study
        MappedImportRelation germplasmRelation = new MappedImportRelation();
        germplasmRelation.setReferenceValue(brapiObservation.getGermplasmName());
        observation.setStudy(germplasmRelation);
        MappedImportRelation ouRelation = new MappedImportRelation();
        ouRelation.setReferenceValue(brapiObservation.getObservationUnitName());
        observation.setObservationUnit(ouRelation);
        MappedImportRelation traitRelation = new MappedImportRelation();
        traitRelation.setReferenceValue(brapiObservation.getObservationVariableName());
        observation.setObservationUnit(traitRelation);

        return observation;
    }

}
