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

package org.breedinginsight.brapps.importer.model.imports.experimentObservation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.*;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;

@Getter
@Setter
@NoArgsConstructor
@ImportConfigMetadata(id="ExperimentImport", name="Experiment Import",
        description = "This import is used to create Observation Unit and Experiment data")
public class ExperimentObservation implements BrAPIImport {

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="germplasmName", name="Germplasm Name", description = "Name of germplasm")
    private String germplasmName;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="gid", name="Germplasm GID", description = "Unique germplasm identifier")
    private String gid;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="test_or_check", name="Test or Check", description = "T test (T) and check (C) germplasm")
    private String testOrCheck;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="exp_title", name="Experiment Title", description = "Title of experiment")
    private String expTitle;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="expDescription", name="Experiment Description", description = "Description of experiment")
    private String expDescription;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="expUnit", name="Experiment Unit", description = "experiment unit  (Examples: plots, plant, tanks, hives, etc.)")
    private String expUnit;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="expType", name="Experiment Type", description = "Description of experimental type (Examples: Performance trial, crossing block, seed orchard, etc)")
    private String expType;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="env", name="Environment", description = "Free-text unique identifier for environment within the experiment. Common examples include: 1,2,3…n and/or a concationation of environment location and year")
    private String env;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="envLocation", name="Environment Location", description = "Location of the environment")
    private String envLocation;

    @ImportFieldType(type= ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id="envYear", name="Environment Year", description = "Year corresponding to the environment")
    private String envYear;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="expUnitId", name="Experiment Unit ID", description = "Human-readable alphanumeric identifier for experimental units unique within environment. Examples, like plot number, are often a numeric sequence.")
    private String expUnitId;

    @ImportFieldType(type= ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id="expReplicateNo", name="Experiment Replicate Number", description = "Sequential number of experimental replications")
    private String expReplicateNo;

    @ImportFieldType(type= ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id="expBlockNo", name="Experiment Block Number", description = "Sequential number of blocks in an experimental design")
    private String expBlockNo;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="row", name="Row", description = "Horizontal (y-axis) position in 2D Cartesian space.")
    private String row;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="column", name="Column", description = "Vertical (x-axis) position in 2D Cartesian space.")
    private String column;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="treatmentFactors", name="Exp Treatment Factor Name", description = "treatement factors in an experiment with applied variables, like fertilizer or water regimens.")
    private String treatmentFactors;

    @ImportFieldType(type= ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id="ObsUnitID", name="Observation Unit ID", description = "A database generated unique identifier for experimental observation units")
    private String ObsUnitID;

    public BrAPITrial constructBrAPITrial(Program program, boolean commit, String referenceSource, UUID id) {
        BrAPIProgram brapiProgram = program.getBrapiProgram();
        BrAPITrial trial = new BrAPITrial();
        if( commit ){
            trial.setTrialName( Utilities.appendProgramKey(getExpTitle(), program.getKey() ));

            // Set external reference
            trial.setExternalReferences(getBrAPIExternalReferences(program, referenceSource, id, null, null));
        }
        else{
            trial.setTrialName( getExpTitle() );
        }
        trial.setTrialDescription(getExpDescription());
        trial.setActive(true);
        trial.setProgramDbId(brapiProgram.getProgramDbId());
        trial.setProgramName(brapiProgram.getProgramName());

        trial.putAdditionalInfoItem("defaultObservationLevel", getExpUnit());
        trial.putAdditionalInfoItem("experimentType", getExpType());

        return trial;
    }

    public BrAPILocation constructBrAPILocation() {
        BrAPILocation location = new BrAPILocation();
        location.setLocationName(getEnvLocation());
        return location;
    }

    public BrAPIStudy constructBrAPIStudy(
            Program program,
            boolean commit,
            String referenceSource,
            String expSequenceValue,
            UUID trialId,
            UUID id) {
        BrAPIStudy study = new BrAPIStudy();
        if ( commit ){
            study.setStudyName(Utilities.appendProgramKey(getEnv(), program.getKey(), expSequenceValue));

            // Set external reference
            study.setExternalReferences(getBrAPIExternalReferences(program, referenceSource, trialId, id,null));
        }
        else {
            study.setStudyName(getEnv());
        }
        study.setActive(true);
        study.setStudyType(getExpType());
        study.setLocationName(getEnvLocation());
        study.setTrialName(getExpTitle());
        study.setSeasons( List.of( getEnvYear()==null ? "" : getEnvYear() ) );

        String designType = "Analysis";
        BrAPIStudyExperimentalDesign design = new BrAPIStudyExperimentalDesign();
        design.setPUI(designType);
        design.setDescription(designType);
        study.setExperimentalDesign(design);

        return study;
    }

    public BrAPIObservationUnit constructBrAPIObservationUnit(
            Program program,
            Supplier<BigInteger> nextVal,
            boolean commit,
            String germplasmName,
            String referenceSource,
            UUID trialID,
            UUID studyID,
            UUID id
    ) {

        BrAPIObservationUnit observationUnit = new BrAPIObservationUnit();
        if( commit){
            observationUnit.setObservationUnitName( Utilities.appendProgramKey(getExpUnitId(), program.getKey(), nextVal.get().toString()));

            // Set external reference
            observationUnit.setExternalReferences(getBrAPIExternalReferences(program, referenceSource, trialID, studyID, id));
        }
        else {
            observationUnit.setObservationUnitName(getExpUnitId());
        }
        observationUnit.setStudyName(getEnv());

        if(germplasmName==null){
            germplasmName = getGermplasmName();
        }
        observationUnit.setGermplasmName(germplasmName);

        BrAPIObservationUnitPosition position = new BrAPIObservationUnitPosition();
        BrAPIObservationUnitLevelRelationship level = new BrAPIObservationUnitLevelRelationship();
        level.setLevelName("plot");  //BreedBase only excepts "plot" or "plant"
        level.setLevelCode( getExpUnitId() );
        position.setObservationLevel(level);
        observationUnit.putAdditionalInfoItem("observationLevel", getExpUnit());

        // Exp Unit
        List<BrAPIObservationUnitLevelRelationship> levelRelationships = new ArrayList<>();
        if( getExpReplicateNo() !=null ) {
            BrAPIObservationUnitLevelRelationship repLvl = new BrAPIObservationUnitLevelRelationship();
            repLvl.setLevelName("replicate");
            repLvl.setLevelCode(getExpReplicateNo());
            levelRelationships.add(repLvl);
        }

        // Block number
        if( getExpBlockNo() != null ) {
            BrAPIObservationUnitLevelRelationship repLvl = new BrAPIObservationUnitLevelRelationship();
            repLvl.setLevelName("block");
            repLvl.setLevelCode(getExpBlockNo());
            levelRelationships.add(repLvl);
        }
        position.setObservationLevelRelationships(levelRelationships);

        // Test or Check
        if("C".equals(getTestOrCheck())){
            position.setEntryType(BrAPIEntryTypeEnum.CHECK);
        } else {
            position.setEntryType(BrAPIEntryTypeEnum.TEST);
        }

        // X and Y coordinates
        if (getRow() != null) {
            position.setPositionCoordinateX(getRow());
            position.setPositionCoordinateXType(BrAPIPositionCoordinateTypeEnum.GRID_ROW);
        }
        if (getColumn() != null) {
            position.setPositionCoordinateY(getColumn());
            position.setPositionCoordinateYType(BrAPIPositionCoordinateTypeEnum.GRID_ROW);
        }
        observationUnit.setObservationUnitPosition(position);

        // Treatment factors
        if (getTreatmentFactors() != null) {
            BrAPIObservationTreatment treatment = new BrAPIObservationTreatment();
            treatment.setFactor(getTreatmentFactors());
            observationUnit.setTreatments(List.of(treatment));
        }

        return observationUnit;
    }

    private List<BrAPIExternalReference> getBrAPIExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId, UUID studyId, UUID obsUnitId) {
        List<BrAPIExternalReference> refs = new ArrayList<>();

        addReference(refs, program.getId(), referenceSourceBaseName, "programs");
        if( trialId   != null ) { addReference(refs, trialId, referenceSourceBaseName, "trials"); }
        if( studyId   != null ) { addReference(refs, studyId, referenceSourceBaseName, "studies"); }
        if( obsUnitId != null ) { addReference(refs, obsUnitId, referenceSourceBaseName, "observationunits"); }

        return refs;
    }

    private void addReference(List<BrAPIExternalReference> refs, UUID uuid, String referenceBaseNameSource, String refSourceName) {
        BrAPIExternalReference reference;
        reference = new BrAPIExternalReference();
        reference.setReferenceSource( String.format("%s/%s", referenceBaseNameSource, refSourceName) );
        reference.setReferenceID(uuid.toString());
        refs.add(reference);
    }

}