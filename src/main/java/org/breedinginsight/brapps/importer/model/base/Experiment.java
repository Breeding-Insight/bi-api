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
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

@Getter
@Setter
@NoArgsConstructor
@ImportFieldMetadata(id="Experiment", name="Experiment",
        description = "An Experiment object corresponds to a non-physical entity and is used to track a unique experiment composition. This is commonly used for populations.")
public class Experiment implements BrAPIObject {

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="germplasmName", name="Germplasm Name", description = "Name of germplasm")
    private String experimentName;
    private String listName;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="gid", name="Germplasm GID", description = "Unique germplasm identifier")
    private String gid;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="test_or_check", name="Test or Check", description = "T test (T) and check (C) germplasm")
    private String test_or_check;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="exp_title", name="Experiment Title", description = "Title of experiment")
    private String exp_title;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="exp_description", name="Experiment Description", description = "Description of experiment")
    private String exp_description;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="exp_unit", name="Experiment Unit", description = "experiment unit  (Examples: plots, plant, tanks, hives, etc.)")
    private String exp_unit;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="exp_type", name="Experiment Type", description = "Description of experimental type (Examples: Performance trial, crossing block, seed orchard, etc)")
    private String exp_type;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="env", name="Environment", description = "Free-text unique identifier for environment within the experiment. Common examples include: 1,2,3…n and/or a concationation of environment location and year")
    private String env;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="env_location", name="Environment Location", description = "Location of the environment")
    private String env_location;

    @ImportFieldType(type= ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id="env_year", name="Environment Year", description = "Year corresponding to the environment")
    private String env_year;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="exp_unit_id", name="Experiment Unit ID", description = "Human-readable alphanumeric identifier for experimental units unique within environment. Examples, like plot number, are often a numeric sequence.")
    private String exp_unit_id;

    @ImportFieldType(type= ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id="exp_replicate_no", name="Experiment Replicate Number", description = "Sequential number of experimental replications")
    private String exp_replicate_no;

    @ImportFieldType(type= ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id="exp_block_no", name="Experiment Block Number", description = "Sequential number of blocks in an experimental design")
    private String exp_block_no;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="row", name="Row", description = "Horizontal (y-axis) position in 2D Cartesian space.")
    private String row;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="column", name="Column", description = "Vertical (x-axis) position in 2D Cartesian space.")
    private String column;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="treatment_factors", name="Treatment Factors", description = "treatement factors in an experiment with applied variables, like fertilizer or water regimens.")
    private String treatment_factors;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="ObsUnitID", name="Observation Unit ID", description = "A database generated unique identifier for experimental observation units")
    private String ObsUnitID;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="pheno_1", name="Phenotype", description = "specification of  ontology variable by name")
    private String pheno_1;

    @ImportFieldType(type= ImportFieldTypeEnum.DATE)
    @ImportFieldMetadata(id="pheno_1_date", name="Phenotype Observation Date/Time", description = "observation timestamp")
    private String pheno_1_date;

    public BrAPITrial constructBrAPITrial(){

    }
//////////////////////////////////////
//    public BrAPIListNewRequest constructBrAPIList(Program program, String referenceSource) {
//        BrAPIListNewRequest brapiList = new BrAPIListNewRequest();
//        brapiList.setListName(constructExperimentListName(listName, program));
//        brapiList.setListDescription(this.listDescription);
//        brapiList.listType(BrAPIListTypes.GERMPLASM);  //TODO get correct listType
//        // Set external reference
//        BrAPIExternalReference reference = new BrAPIExternalReference();
//        reference.setReferenceSource(String.format("%s/programs", referenceSource));
//        reference.setReferenceID(program.getId().toString());
//        brapiList.setExternalReferences(List.of(reference));
//        return brapiList;
//    }

    public static String constructExperimentListName(String listName, Program program) {
        return String.format("%s [%s-experiment]", listName, program.getKey());
    }

    private void setBrAPIGermplasmCommitFields(BrAPIGermplasm germplasm, String programKey, String referenceSource, Supplier<BigInteger> nextVal) {

        // Add UUID external reference
        UUID newUUID = UUID.randomUUID();
        BrAPIExternalReference newReference = new BrAPIExternalReference();
        newReference.setReferenceSource(referenceSource);
        newReference.setReferenceID(newUUID.toString());
        germplasm.getExternalReferences().add(newReference);

        // Get the next accession number
        germplasm.setAccessionNumber(nextVal.get().toString());

        // Set germplasm name to <Name> [<program key>-<accessionNumber>]
        germplasm.setGermplasmName(String.format("%s [%s-%s]", germplasm.getDefaultDisplayName(), programKey, germplasm.getAccessionNumber()));

        // Set createdDate field
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        germplasm.putAdditionalInfoItem("createdDate", formatter.format(now));
    }

//    public BrAPIGermplasm constructBrAPIGermplasm(Program program, BreedingMethodEntity breedingMethod, User user, boolean commit, String referenceSource, Supplier<BigInteger> nextVal) {
//        BrAPIGermplasm germplasm = constructBrAPIGermplasm(breedingMethod, user);
//        if (commit) {
//            setBrAPIGermplasmCommitFields(germplasm, program.getKey(), referenceSource, nextVal);
//        }
//        germplasm.setCommonCropName(program.getBrapiProgram().getCommonCropName());
//
//        // Set program id in external references
//        BrAPIExternalReference newReference = new BrAPIExternalReference();
//        newReference.setReferenceSource(String.format("%s/programs", referenceSource));
//        newReference.setReferenceID(program.getId().toString());
//        germplasm.getExternalReferences().add(newReference);
//
//        return germplasm;
//    }

}