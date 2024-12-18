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

import com.github.filosganga.geogson.model.Feature;
import com.github.filosganga.geogson.model.Point;
import com.github.filosganga.geogson.model.positions.SinglePosition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.BrApiGeoJSON;
import org.brapi.v2.model.core.*;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.config.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.*;
import org.breedinginsight.utilities.Utilities;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

@Getter
@Setter
@NoArgsConstructor
@ImportConfigMetadata(id = "ExperimentImport", name = "Experiment Import",
        description = "This import is used to create Observation Unit and Experiment data")
public class ExperimentObservation implements BrAPIImport {
    @ImportFieldType(type = ImportFieldTypeEnum.BOOLEAN, collectTime = ImportCollectTimeEnum.UPLOAD)
    @ImportFieldMetadata(id = "overwrite", name = "Overwrite", description = "Boolean flag to overwrite existing observation")
    private String overwrite;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT, collectTime = ImportCollectTimeEnum.UPLOAD)
    @ImportFieldMetadata(id="overwriteReason", name="Overwrite Reason", description="Description of the reason for overwriting existing observations")
    private String overwriteReason;

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
    @ImportFieldMetadata(id = "subObsUnit", name = Columns.SUB_OBS_UNIT, description = "Sub observation unit (Examples: plant, etc.)")
    private String subObsUnit;

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

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "subUnitId", name = Columns.SUB_UNIT_ID, description = "Alphanumeric identifier of sub-units. For example if three fruits are observed per plot, the three fruits can be identified by 1,2,3.")
    private String subUnitId;

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
    @ImportFieldMetadata(id = "lat", name = Columns.LAT, description = "Latitude coordinate in WGS 84 coordinate reference system.")
    private String latitude;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "long", name = Columns.LONG, description = "Longitude coordinate in WGS 84 coordinate reference system.")
    private String longitude;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "elevation", name = Columns.ELEVATION, description = "Height in meters above WGS 84 reference ellipsoid.")
    private String elevation;

    @ImportFieldType(type = ImportFieldTypeEnum.TEXT)
    @ImportFieldMetadata(id = "rtk", name = Columns.RTK, description = "Free text description of real-time kinematic positioning used to correct coordinates.")
    private String rtk;

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

    public BrAPIListDetails constructDatasetDetails(
            String name,
            UUID datasetId,
            String referenceSourceBase,
            Program program, String trialId) {
        BrAPIListDetails dataSetDetails = new BrAPIListDetails();
        dataSetDetails.setListName(name);
        dataSetDetails.setListType(BrAPIListTypes.OBSERVATIONVARIABLES);
        dataSetDetails.setData(new ArrayList<>());
        dataSetDetails.putAdditionalInfoItem("datasetType", "observationDataset");
        List<BrAPIExternalReference> refs = new ArrayList<>();
        Utilities.addReference(refs, program.getId(), referenceSourceBase, ExternalReferenceSource.PROGRAMS);
        Utilities.addReference(refs, UUID.fromString(trialId), referenceSourceBase, ExternalReferenceSource.TRIALS);
        Utilities.addReference(refs, datasetId, referenceSourceBase, ExternalReferenceSource.DATASET);
        dataSetDetails.setExternalReferences(refs);
        return dataSetDetails;
    }
    public BrAPIObservationUnit constructBrAPIObservationUnit(
            Program program,
            String seqVal,
            boolean commit,
            String germplasmName,
            String gid,
            String referenceSource,
            UUID trialID,
            UUID datasetId,
            UUID studyID,
            UUID id
    ) {

        BrAPIObservationUnit observationUnit = new BrAPIObservationUnit();
        if (commit) {
            observationUnit.setObservationUnitName(Utilities.appendProgramKey(getExpUnitId(), program.getKey(), seqVal));

            // Set external reference
            observationUnit.setExternalReferences(getObsUnitExternalReferences(program, referenceSource, trialID, datasetId, studyID, id));
        } else {
            observationUnit.setObservationUnitName(getExpUnitId());
        }
        observationUnit.setStudyName(getEnv());

        if (germplasmName == null) {
            germplasmName = getGermplasmName();
        }
        observationUnit.setGermplasmName(germplasmName);
        observationUnit.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GID, gid);

        BrAPIObservationUnitPosition position = new BrAPIObservationUnitPosition();
        BrAPIObservationUnitLevelRelationship level = new BrAPIObservationUnitLevelRelationship();
        // If expUnit is null, a validation error will be produced later on.
        if (getExpUnit() != null)
        {
            // TODO: [BI-2219] BJTS only accepts hardcoded levels, need to handle dynamic levels.
            level.setLevelName(getExpUnit().toLowerCase());  // HACK: toLowerCase() is needed to match BJTS hardcoded levels.
        }
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
        String testOrCheckUpperCase = getTestOrCheck();
        testOrCheckUpperCase = ( (testOrCheckUpperCase==null) ? "": testOrCheckUpperCase.toUpperCase() );

        if ("C".equals(testOrCheckUpperCase) || "CHECK".equals(testOrCheckUpperCase)) {
            position.setEntryType(BrAPIEntryTypeEnum.CHECK);
        } else {
            position.setEntryType(BrAPIEntryTypeEnum.TEST);
        }

        // geocoordinates
        try {
            double lat = Double.parseDouble(getLatitude());
            double lon = Double.parseDouble(getLongitude());
            Point geoPoint = Point.from(lon, lat);

            if (getElevation() != null) {
                double elevation = Double.parseDouble(getElevation());
                geoPoint = Point.from(lon, lat, elevation); // geoPoint.withAlt(elevation) did not work
            }

            BrApiGeoJSON coords = BrApiGeoJSON.builder()
                    .geometry(geoPoint)
                    .type("Feature")
                    .build();
            position.setGeoCoordinates(coords);

        } catch (NullPointerException | NumberFormatException e) {
            // ignore null or number format exceptions, won't populate geocoordinates if there are any issues
        }

        String rtk = getRtk();
        if (StringUtils.isNotBlank(rtk)) {
            observationUnit.putAdditionalInfoItem(BrAPIAdditionalInfoFields.RTK, rtk);
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
            BrAPIObservationUnit obsUnit,
            boolean commit,
            Program program,
            User user,
            String referenceSource,
            UUID trialID,
            UUID studyID,
            UUID obsUnitID,
            UUID id) {
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

        if(commit) {
            Map<String, Object> createdBy = new HashMap<>();
            createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_ID, user.getId());
            createdBy.put(BrAPIAdditionalInfoFields.CREATED_BY_USER_NAME, user.getName());
            observation.putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_BY, createdBy);
            observation.putAdditionalInfoItem(BrAPIAdditionalInfoFields.CREATED_DATE, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()));

            observation.setExternalReferences(getObservationExternalReferences(program, referenceSource, trialID, studyID, obsUnitID, id));
        }

        return observation;
    }

    private List<BrAPIExternalReference> getBrAPIExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId, UUID datasetId, UUID studyId, UUID obsUnitId, UUID observationId) {
        List<BrAPIExternalReference> refs = new ArrayList<>();

        Utilities.addReference(refs, program.getId(), referenceSourceBaseName, ExternalReferenceSource.PROGRAMS);
        if (trialId != null) {
            Utilities.addReference(refs, trialId, referenceSourceBaseName, ExternalReferenceSource.TRIALS);
        }
        if (datasetId != null) {
            Utilities.addReference(refs, datasetId, referenceSourceBaseName, ExternalReferenceSource.DATASET);
        }
        if (studyId != null) {
            Utilities.addReference(refs, studyId, referenceSourceBaseName, ExternalReferenceSource.STUDIES);
        }
        if (obsUnitId != null) {
            Utilities.addReference(refs, obsUnitId, referenceSourceBaseName, ExternalReferenceSource.OBSERVATION_UNITS);
        }
        if (observationId != null) {
            Utilities.addReference(refs, observationId, referenceSourceBaseName, ExternalReferenceSource.OBSERVATIONS);
        }

        return refs;
    }

    private List<BrAPIExternalReference> getTrialExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId) {
        return getBrAPIExternalReferences(program, referenceSourceBaseName, trialId, null, null, null, null);
    }

    private List<BrAPIExternalReference> getStudyExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId, UUID studyId) {
        return getBrAPIExternalReferences(program, referenceSourceBaseName, trialId, null, studyId, null, null);
    }

    private List<BrAPIExternalReference> getObsUnitExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId, UUID datasetId, UUID studyId, UUID obsUnitId) {
        return getBrAPIExternalReferences(program, referenceSourceBaseName, trialId, datasetId, studyId, obsUnitId, null);
    }

    private List<BrAPIExternalReference> getObservationExternalReferences(
            Program program, String referenceSourceBaseName, UUID trialId, UUID studyId, UUID obsUnitId, UUID observationId) {
        return getBrAPIExternalReferences(program, referenceSourceBaseName, trialId, null, studyId, obsUnitId, observationId);
    }

    public static final class Columns {
        public static final String GERMPLASM_NAME = "Germplasm Name";
        public static final String GERMPLASM_GID = "Germplasm GID";
        public static final String TEST_CHECK = "Test (T) or Check (C)";
        public static final String EXP_TITLE = "Exp Title";
        public static final String EXP_DESCRIPTION = "Exp Description";
        public static final String EXP_UNIT = "Exp Unit";
        public static final String SUB_OBS_UNIT = "Sub-Obs Unit";
        public static final String EXP_TYPE = "Exp Type";
        public static final String ENV = "Env";
        public static final String ENV_LOCATION = "Env Location";
        public static final String ENV_YEAR = "Env Year";
        public static final String EXP_UNIT_ID = "Exp Unit ID";
        public static final String SUB_UNIT_ID = "Sub Unit ID";
        public static final String REP_NUM = "Exp Replicate #";
        public static final String BLOCK_NUM = "Exp Block #";
        public static final String ROW = "Row";
        public static final String COLUMN = "Column";
        public static final String LAT = "Lat";
        public static final String LONG = "Long";
        public static final String ELEVATION = "Elevation";
        public static final String RTK = "RTK";
        public static final String TREATMENT_FACTORS = "Treatment Factors";
        public static final String OBS_UNIT_ID = "ObsUnitID";
    }

}
