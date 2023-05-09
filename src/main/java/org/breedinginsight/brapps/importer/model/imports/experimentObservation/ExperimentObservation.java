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
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.BrAPIConstants;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.User;
import org.breedinginsight.utilities.Utilities;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

@Getter
@Setter
@NoArgsConstructor
@ImportConfigMetadata(id = "ExperimentImport", name = "Experiment Import",
        description = "This import is used to create Observation Unit and Experiment data")
public class ExperimentObservation implements BrAPIImport {

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "germplasmName", name = Columns.GERMPLASM_NAME, description = "Name of germplasm")
    private String germplasmName;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "gid", name = Columns.GERMPLASM_GID, description = "Unique germplasm identifier")
    private String gid;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "test_or_check", name = Columns.TEST_CHECK, description = "T test (T) and check (C) germplasm")
    private String testOrCheck;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "exp_title", name = Columns.EXP_TITLE, description = "Title of experiment")
    private String expTitle;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "expDescription", name = Columns.EXP_DESCRIPTION, description = "Description of experiment")
    private String expDescription;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "expUnit", name = Columns.EXP_UNIT, description = "Experiment unit  (Examples: plots, plant, tanks, hives, etc.)")
    private String expUnit;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "expType", name = Columns.EXP_TYPE, description = "Description of experimental type (Examples: Performance trial, crossing block, seed orchard, etc)")
    private String expType;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "env", name = Columns.ENV, description = "Free-text unique identifier for environment within the experiment. Common examples include: 1,2,3…n and/or a concationation of environment location and year")
    private String env;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "envLocation", name = Columns.ENV_LOCATION, description = "Location of the environment")
    private String envLocation;

    @ImportFieldType(type = ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id = "envYear", name = Columns.ENV_YEAR, description = "Year corresponding to the environment")
    private String envYear;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "expUnitId", name = Columns.EXP_UNIT_ID, description = "Human-readable alphanumeric identifier for experimental units unique within environment. Examples, like plot number, are often a numeric sequence.")
    private String expUnitId;

    @ImportFieldType(type = ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id = "expReplicateNo", name = Columns.REP_NUM, description = "Sequential number of experimental replications")
    private String expReplicateNo;

    @ImportFieldType(type = ImportFieldTypeEnum.INTEGER)
    @ImportFieldMetadata(id = "expBlockNo", name = Columns.BLOCK_NUM, description = "Sequential number of blocks in an experimental design")
    private String expBlockNo;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "row", name = Columns.ROW, description = "Horizontal (y-axis) position in 2D Cartesian space.")
    private String row;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "column", name = Columns.COLUMN, description = "Vertical (x-axis) position in 2D Cartesian space.")
    private String column;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "treatmentFactors", name = Columns.TREATMENT_FACTORS, description = "Treatment factors in an experiment with applied variables, like fertilizer or water regimens.")
    private String treatmentFactors;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "ObsUnitID", name = Columns.OBS_UNIT_ID, description = "A database generated unique identifier for experimental observation units")
    private String obsUnitID;

    public BrAPITrial constructBrAPITrial(Program program, User user, boolean commit, String referenceSource, UUID id, String expSeqValue) {
        BrAPIProgram brapiProgram = program.getBrapiProgram();
        BrAPITrial trial = new BrAPITrial();
        if (commit) {
            setBrAPITrialCommitFields(program, trial, referenceSource, id);
        } else {
            trial.setTrialName(getExpTitle());
        }
        trial.setTrialDescription(getExpDescription());
        trial.setActive(true);
        trial.setProgramDbId(brapiProgram.getProgramDbId());
        trial.setProgramName(brapiProgram.getProgramName());

        Map<String, String> createdBy = new HashMap<>();
        createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_ID,
                      user.getId()
                          .toString());
        createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_NAME, user.getName());
        trial.putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_BY, createdBy);
        trial.putAdditionalInfoItem(BrAPIAdditionalInfoFields.DEFAULT_OBSERVATION_LEVEL, getExpUnit());
        trial.putAdditionalInfoItem(BrAPIAdditionalInfoFields.EXPERIMENT_TYPE, getExpType());
        trial.putAdditionalInfoItem(BrAPIAdditionalInfoFields.EXPERIMENT_NUMBER, expSeqValue);

        return trial;
    }

    private void setBrAPITrialCommitFields(Program program, BrAPITrial trial, String referenceSource, UUID id) {
        trial.setTrialName(Utilities.appendProgramKey(getExpTitle(), program.getKey()));

        // Set external reference
        trial.setExternalReferences(getTrialExternalReferences(program, referenceSource, id));

        // Set createdDate field
        LocalDateTime now = LocalDateTime.now();
        trial.putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_DATE, DateTimeFormatter.ISO_LOCAL_DATE.format(now));

    }

    public ProgramLocation constructProgramLocation() {
        ProgramLocation location = new ProgramLocation();
        location.setName(getEnvLocation());
        return location;
    }

    public BrAPIStudy constructBrAPIStudy(
            Program program,
            boolean commit,
            String referenceSource,
            String expSequenceValue,
            UUID trialId,
            UUID id,
            Supplier<BigInteger> envNextVal) {
        BrAPIStudy study = new BrAPIStudy();
        if (commit) {
            study.setStudyName(Utilities.appendProgramKey(getEnv(), program.getKey(), expSequenceValue));

            // Set external reference
            study.setExternalReferences(getStudyExternalReferences(program, referenceSource, trialId, id));
        } else {
            study.setStudyName(getEnv());
        }
        study.setActive(true);
        study.setStudyType(getExpType());
        study.setLocationName(getEnvLocation());
        study.setTrialName(getExpTitle());

        List<String> seasonList = new ArrayList<>();
        seasonList.add(getEnvYear());
        study.setSeasons(seasonList);

        String designType = "Analysis"; // to support the BRApi server, the design type must be one of the following:
        // 'CRD','Alpha','MAD','Lattice','Augmented','RCBD','p-rep','splitplot','greenhouse','Westcott', or 'Analysis'
        // For now it will be hardcoded to 'Analysis'
        BrAPIStudyExperimentalDesign design = new BrAPIStudyExperimentalDesign();
        design.setPUI(designType);
        design.setDescription(designType);
        study.setExperimentalDesign(design);
        if (commit) {
            study.putAdditionalInfoItem(BrAPIAdditionalInfoFields.ENVIRONMENT_NUMBER, envNextVal.get().toString());
        }
        return study;
    }

    public BrAPIObservationUnit constructBrAPIObservationUnit(
            Program program,
            String seqVal,
            boolean commit,
            String germplasmName,
            String referenceSource,
            UUID trialID,
            UUID studyID,
            UUID id
    ) {

        BrAPIObservationUnit observationUnit = new BrAPIObservationUnit();
        if (commit) {
            observationUnit.setObservationUnitName(Utilities.appendProgramKey(getExpUnitId(), program.getKey(), seqVal));

            // Set external reference
            observationUnit.setExternalReferences(getObsUnitExternalReferences(program, referenceSource, trialID, studyID, id));
        } else {
            observationUnit.setObservationUnitName(getExpUnitId());
        }
        observationUnit.setStudyName(getEnv());

        if (germplasmName == null) {
            germplasmName = getGermplasmName();
        }
        observationUnit.setGermplasmName(germplasmName);

        BrAPIObservationUnitPosition position = new BrAPIObservationUnitPosition();
        BrAPIObservationUnitLevelRelationship level = new BrAPIObservationUnitLevelRelationship();
        level.setLevelName("plot");  //BreedBase only accepts "plot" or "plant"
        level.setLevelCode(Utilities.appendProgramKey(getExpUnitId(), program.getKey(), seqVal));
        position.setObservationLevel(level);
        observationUnit.putAdditionalInfoItem(BrAPIAdditionalInfoFields.OBSERVATION_LEVEL, getExpUnit());

        // Exp Unit
        List<BrAPIObservationUnitLevelRelationship> levelRelationships = new ArrayList<>();
        if (getExpReplicateNo() != null) {
            BrAPIObservationUnitLevelRelationship repLvl = new BrAPIObservationUnitLevelRelationship();
            repLvl.setLevelName(BrAPIConstants.REPLICATE.getValue());
            repLvl.setLevelCode(getExpReplicateNo());
            levelRelationships.add(repLvl);
        }

        // Block number
        if (getExpBlockNo() != null) {
            BrAPIObservationUnitLevelRelationship blockLvl = new BrAPIObservationUnitLevelRelationship();
            blockLvl.setLevelName(BrAPIConstants.BLOCK.getValue());
            blockLvl.setLevelCode(getExpBlockNo());
            levelRelationships.add(blockLvl);
        }
        position.setObservationLevelRelationships(levelRelationships);

        // Test or Check
        if ("C".equals(getTestOrCheck())) {
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
            position.setPositionCoordinateYType(BrAPIPositionCoordinateTypeEnum.GRID_COL);
        }
        observationUnit.setObservationUnitPosition(position);

        // Treatment factors
        if (getTreatmentFactors() != null) {
            BrAPIObservationTreatment treatment = new BrAPIObservationTreatment();
            treatment.setFactor(getTreatmentFactors());
            observationUnit.setTreatments(List.of(treatment));
        }

        if (getObsUnitID() != null) {
            observationUnit.setObservationUnitDbId(getObsUnitID());
        }

        return observationUnit;
    }

    public BrAPIObservation constructBrAPIObservation(
            String value,
            String variableName,
            String seasonDbId,
            BrAPIObservationUnit obsUnit
    ) {
        BrAPIObservation observation = new BrAPIObservation();
        observation.setGermplasmName(getGermplasmName());
        if (getEnv() != null) {
            observation.putAdditionalInfoItem(BrAPIAdditionalInfoFields.STUDY_NAME, getEnv());
        }
        observation.setObservationVariableName(variableName);
        observation.setObservationUnitDbId(obsUnit.getObservationUnitDbId());
        observation.setObservationUnitName(obsUnit.getObservationUnitName());
        observation.setValue(value);

        // The BrApi server needs this.  Breedbase does not.
        BrAPISeason season = new BrAPISeason();
        season.setSeasonDbId(seasonDbId);
        observation.setSeason(season);

        return observation;
    }

    private List<BrAPIExternalReference> getBrAPIExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId, UUID studyId, UUID obsUnitId) {
        List<BrAPIExternalReference> refs = new ArrayList<>();

        addReference(refs, program.getId(), referenceSourceBaseName, ExternalReferenceSource.PROGRAMS);
        if (trialId != null) {
            addReference(refs, trialId, referenceSourceBaseName, ExternalReferenceSource.TRIALS);
        }
        if (studyId != null) {
            addReference(refs, studyId, referenceSourceBaseName, ExternalReferenceSource.STUDIES);
        }
        if (obsUnitId != null) {
            addReference(refs, obsUnitId, referenceSourceBaseName, ExternalReferenceSource.OBSERVATION_UNITS);
        }

        return refs;
    }

    private List<BrAPIExternalReference> getTrialExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId) {
        return getBrAPIExternalReferences(program, referenceSourceBaseName, trialId, null, null);
    }

    private List<BrAPIExternalReference> getStudyExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId, UUID studyId) {
        return getBrAPIExternalReferences(program, referenceSourceBaseName, trialId, studyId, null);
    }

    private List<BrAPIExternalReference> getObsUnitExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId, UUID studyId, UUID obsUnitId) {
        return getBrAPIExternalReferences(program, referenceSourceBaseName, trialId, studyId, obsUnitId);
    }


    private void addReference(List<BrAPIExternalReference> refs, UUID uuid, String referenceBaseNameSource, ExternalReferenceSource refSourceName) {
        BrAPIExternalReference reference;
        reference = new BrAPIExternalReference();
        reference.setReferenceSource(String.format("%s/%s", referenceBaseNameSource, refSourceName.getName()));
        reference.setReferenceID(uuid.toString());
        refs.add(reference);
    }

    public static final class Columns {
        public static final String GERMPLASM_NAME = "Germplasm Name";
        public static final String GERMPLASM_GID = "Germplasm GID";
        public static final String TEST_CHECK = "Test (T) or Check (C )";
        public static final String EXP_TITLE = "Exp Title";
        public static final String EXP_DESCRIPTION = "Exp Description";
        public static final String EXP_UNIT = "Exp Unit";
        public static final String EXP_TYPE = "Exp Type";
        public static final String ENV = "Env";
        public static final String ENV_LOCATION = "Env Location";
        public static final String ENV_YEAR = "Env Year";
        public static final String EXP_UNIT_ID = "Exp Unit ID";
        public static final String REP_NUM = "Exp Replicate #";
        public static final String BLOCK_NUM = "Exp Block #";
        public static final String ROW = "Row";
        public static final String COLUMN = "Column";
        public static final String TREATMENT_FACTORS = "Treatment Factors";
        public static final String OBS_UNIT_ID = "ObsUnitID";
    }

}
