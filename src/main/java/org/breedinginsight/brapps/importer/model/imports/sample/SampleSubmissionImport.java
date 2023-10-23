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

package org.breedinginsight.brapps.importer.model.imports.sample;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.geno.BrAPIPlate;
import org.brapi.v2.model.geno.BrAPIPlateFormat;
import org.brapi.v2.model.geno.BrAPISample;
import org.brapi.v2.model.geno.BrAPISampleTypeEnum;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;

import java.util.*;

@Getter
@Setter
@NoArgsConstructor
@ImportConfigMetadata(id = "SampleImport", name = "Sample Import",
        description = "This import is used to create Genotype Samples")
public class SampleSubmissionImport implements BrAPIImport {

    private static final String SAMPLE_NAME_FORMAT = "%s__%s_%s%s";

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "plateId", name = Columns.PLATE_ID, description = "The ID which uniquely identifies this plate to the client making the request")
    private String plateId;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "row", name = Columns.ROW, description = "The Row identifier for this samples location in the plate, ex: B")
    private String row;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "column", name = Columns.COLUMN, description = "The Column identifier for this samples location in the plate, ex: 6")
    private String column;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "organism", name = Columns.ORGANISM, description = "Scientific organism name")
    private String organism;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "species", name = Columns.SPECIES, description = "Scientific species name")
    private String species;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "germplasmName", name = Columns.GERMPLASM_NAME, description = "Name of germplasm")
    private String germplasmName;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "gid", name = Columns.GERMPLASM_GID, description = "Unique germplasm identifier")
    private String gid;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "obsUnitId", name = Columns.OBS_UNIT_ID, description = "The Observation Unit that this sample was collected from")
    private String obsUnitId;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "tissue", name = Columns.TISSUE, description = "The type of tissue in this sample. List of accepted tissue types can be found in the Vendor Specs.")
    private String tissue;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "comment", name = Columns.COMMENT, description = "Generic comments about this sample for the vendor")
    private String comment;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT, collectTime = ImportCollectTimeEnum.UPLOAD)
    @ImportMappingRequired
    @ImportFieldMetadata(id="submissionName", name="Submission Name", description = "Name of the submission imported.")
    private String submissionName;

    public static final class Columns {
        public static final String PLATE_ID = "PlateID";
        public static final String ROW = "Row";
        public static final String COLUMN = "Column";
        public static final String ORGANISM = "Organism";
        public static final String SPECIES = "Species";
        public static final String GERMPLASM_NAME = "Germplasm Name";
        public static final String GERMPLASM_GID = "Germplasm GID";
        public static final String TISSUE = "Tissue";
        public static final String COMMENT = "Comment";
        public static final String OBS_UNIT_ID = "ObsUnitID";
    }

    public BrAPISample constructBrAPISample(boolean commit, Program program, User user, BrAPIPlate plate, String referenceSource, String submissionId, BrAPIGermplasm germplasm, BrAPIObservationUnit ou) {
        List<BrAPIExternalReference> xrefs = new ArrayList<>();
        xrefs.add(new BrAPIExternalReference().referenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS))
                                              .referenceId(program.getId()
                                                                  .toString()));
        String germXrefSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.GERMPLASM);
        xrefs.add(germplasm.getExternalReferences()
                           .stream()
                           .filter(xref -> xref.getReferenceSource().equals(referenceSource) || xref.getReferenceSource().equals(germXrefSource))
                           .findFirst()
                           .orElseThrow(() -> new IllegalStateException(String.format("Germplasm %s doesn't have an xref! -> %s", germplasm.getAccessionNumber(), germplasm.toString()))).referenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.GERMPLASM)));
        if(commit) {
            xrefs.add(new BrAPIExternalReference().referenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.SAMPLES))
                                                  .referenceId(UUID.randomUUID().toString()));
            xrefs.add(Utilities.getExternalReference(plate.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATES))
                               .get());
        }
        if (submissionId != null) {
            xrefs.add(new BrAPIExternalReference().referenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATE_SUBMISSIONS))
                                                  .referenceId(submissionId));
        }

        Map<String, String> createdBy = new HashMap<>();
        createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_ID,
                      user.getId().toString());
        createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_NAME, user.getName());

        BrAPISample brAPISample = new BrAPISample()
                .putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_BY, createdBy)
                .putAdditionalInfoItem(BrAPIAdditionalInfoFields.SAMPLE_SPECIES, species)
                .putAdditionalInfoItem(BrAPIAdditionalInfoFields.SAMPLE_ORGANISM, organism)
                .putAdditionalInfoItem(BrAPIAdditionalInfoFields.GID, germplasm.getAccessionNumber())
                .putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_NAME, germplasm.getDefaultDisplayName())
                .externalReferences(xrefs)
                .plateName(plateId)
                .plateDbId(plate.getPlateDbId())
                .sampleName(String.format(SAMPLE_NAME_FORMAT, germplasm.getDefaultDisplayName(), plate.getPlateName(), row, column))
                .germplasmDbId(germplasm.getGermplasmDbId())
                .row(row)
                .column(Integer.valueOf(column))
                .tissueType(tissue)
                .sampleDescription(comment);

        if (ou != null) {
            brAPISample
                    .putAdditionalInfoItem(BrAPIAdditionalInfoFields.OBS_UNIT_ID,
                                           Utilities.getExternalReference(ou.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.OBSERVATION_UNITS))
                                                    .get()
                                                    .getReferenceId())
                    .observationUnitDbId(ou.getObservationUnitDbId());
        }

        return brAPISample;
    }

    public BrAPIPlate constructBrAPIPlate(boolean commit, Program program, User user, String referenceSource, String submissionId) {
        List<BrAPIExternalReference> xrefs = new ArrayList<>();
        xrefs.add(new BrAPIExternalReference().referenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS))
                                              .referenceId(program.getId()
                                                                  .toString()));

        BrAPIPlate brAPIPlate = new BrAPIPlate();
        if(commit) {
            xrefs.add(new BrAPIExternalReference().referenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATES))
                                                  .referenceId(UUID.randomUUID().toString()));
        }
        if (submissionId != null) {
            xrefs.add(new BrAPIExternalReference().referenceSource(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATE_SUBMISSIONS))
                                                  .referenceId(submissionId));
            brAPIPlate.putAdditionalInfoItem(BrAPIAdditionalInfoFields.SUBMISSION_NAME, submissionName);
        }
        Map<String, String> createdBy = new HashMap<>();
        createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_ID,
                      user.getId()
                          .toString());
        createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_NAME, user.getName());



        return brAPIPlate
                .externalReferences(xrefs)
                .putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_BY, createdBy)
                .plateName(plateId)
                .sampleType(BrAPISampleTypeEnum.fromValue(tissue.toUpperCase()))
                .plateFormat(BrAPIPlateFormat.PLATE_96);

    }
}
