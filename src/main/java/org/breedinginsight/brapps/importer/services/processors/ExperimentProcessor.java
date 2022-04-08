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
package org.breedinginsight.brapps.importer.services.processors;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.*;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.*;
import org.breedinginsight.brapps.importer.model.base.Germplasm;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class ExperimentProcessor implements Processor {

    private static final String NAME = "Experiment";

    private FileData fileData = new FileData();

    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    private BrAPIGermplasmService brAPIGermplasmService;
    private BreedingMethodDAO breedingMethodDAO;
    private BrAPIListDAO brAPIListDAO;
    private DSLContext dsl;

    Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByAccessionNumber = new HashMap<>();
    Map<String, Integer> fileGermplasmByName = new HashMap<>();
    Map<String, BrAPIGermplasm> dbGermplasmByName = new HashMap<>();
    Map<String, Integer> germplasmIndexByEntryNo = new HashMap<>();
    List<BrAPIGermplasm> newExperimentList;
    List<BrAPIGermplasm> existingGermplasms;
    List<BrAPIGermplasm> existingParentGermplasms;
    List<List<BrAPIGermplasm>> postOrder = new ArrayList<>();
    BrAPIListNewRequest importList = new BrAPIListNewRequest();

    public static String missingParentalDbIdsMsg = "The following parental GIDs were not found in the database: %s.";
    public static String missingParentalEntryNoMsg = "The following parental entry numbers were not found in the database: %s.";
    public static String badBreedMethodsMsg = "Invalid breeding method";
    public static String missingEntryNumbersMsg = "Either all or none of the germplasm must have entry numbers.";
    public static String duplicateEntryNoMsg = "Entry numbers must be unique. Duplicated entry numbers found: %s";
    public static String circularDependency = "Circular dependency in the pedigree tree";
    public static String listNameAlreadyExists = "Import group name already exists";
    public static Function<List<String>, String> arrayOfStringFormatter = (lst) -> {
        List<String> lstCopy = new ArrayList<>(lst);
        Collections.sort(lstCopy);
        return StringUtils.join(lstCopy, ", ");
    };

    @Inject
    public ExperimentProcessor(BrAPIGermplasmService brAPIGermplasmService, DSLContext dsl, BreedingMethodDAO breedingMethodDAO, BrAPIListDAO brAPIListDAO) {
        this.brAPIGermplasmService = brAPIGermplasmService;
        this.dsl = dsl;
        this.breedingMethodDAO = breedingMethodDAO;
        this.brAPIListDAO = brAPIListDAO;
        this.brAPIGermplasmService = brAPIGermplasmService;
    }

    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) throws ApiException {

        // Get all of our objects specified in the data file by their unique attributes
        Set<String> germplasmDBIDs = new HashSet<>();
        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport germplasmImport = importRows.get(i);
            if (germplasmImport.getGermplasm() != null) {

                // Retrieve parent dbids to assess if already in db
                if (germplasmImport.getGermplasm().getFemaleParentDBID() != null) {
                    germplasmDBIDs.add(germplasmImport.getGermplasm().getFemaleParentDBID());
                }
                if (germplasmImport.getGermplasm().getMaleParentDBID() != null) {
                    germplasmDBIDs.add(germplasmImport.getGermplasm().getMaleParentDBID());
                }

                //Retrieve entry numbers of file for comparison with parent entry numbers
                if (germplasmImport.getGermplasm().getEntryNo()!= null) {
                    germplasmIndexByEntryNo.put(germplasmImport.getGermplasm().getEntryNo(), i);
                }

                Integer count = fileGermplasmByName.getOrDefault(germplasmImport.getGermplasm().getGermplasmName(), 0);
                fileGermplasmByName.put(germplasmImport.getGermplasm().getGermplasmName(), count+1);
            }
        }

        // If parental DBID, should also be in database
        existingGermplasms = new ArrayList<>();
        List<String> missingDbIds = new ArrayList<>(germplasmDBIDs);
        if (germplasmDBIDs.size() > 0) {
            try {
                existingParentGermplasms = brAPIGermplasmService.getRawGermplasmByAccessionNumber(new ArrayList<>(germplasmDBIDs), program.getId());
                List<String> existingDbIds = existingParentGermplasms.stream()
                        .map(germplasm -> germplasm.getAccessionNumber())
                        .collect(Collectors.toList());
                missingDbIds.removeAll(existingDbIds);

                existingParentGermplasms.forEach(existingGermplasm -> {
                    germplasmByAccessionNumber.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm));
                });
                //Since parent germplasms need to be present for check re circular dependencies
                existingGermplasms.addAll(existingParentGermplasms);
            } catch (ApiException e) {
                // We shouldn't get an error back from our services. If we do, nothing the user can do about it
                throw new InternalServerException(e.toString(), e);
            }
        }

        // Get existing germplasm names
        List<BrAPIGermplasm> dbGermplasm = brAPIGermplasmService.getGermplasmByDisplayName(new ArrayList<>(fileGermplasmByName.keySet()), program.getId());
        dbGermplasm.stream().forEach(germplasm -> dbGermplasmByName.put(germplasm.getDefaultDisplayName(), germplasm));

        // Check for existing germplasm lists
        Boolean listNameDup = false;
        if (importRows.size() > 0 && importRows.get(0).getGermplasm().getListName() != null) {
            try {
                Germplasm row = importRows.get(0).getGermplasm();
                String listName = row.constructGermplasmListName(row.getListName(), program);
                List<BrAPIListSummary> existingLists = brAPIListDAO.getListByName(List.of(listName), program.getId());
                for (BrAPIListSummary existingList: existingLists) {
                    if (existingList.getListName().equals(listName)) {
                        listNameDup = true;
                    }
                }
            } catch (ApiException e) {
                throw new InternalServerException(e.toString(), e);
            }
        }

        // Parent reference checks
        if (missingDbIds.size() > 0) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    String.format(missingParentalDbIdsMsg,
                            arrayOfStringFormatter.apply(missingDbIds)));
        }

        List<String> missingEntryNumbers = new ArrayList<>();
        for (BrAPIImport importRow: importRows) {
            Germplasm germplasm = importRow.getGermplasm();
            // Check Female Parent
            if (germplasm.getFemaleParentEntryNo() != null) {
                if (!germplasmIndexByEntryNo.containsKey(germplasm.getFemaleParentEntryNo())) {
                    missingEntryNumbers.add(germplasm.getFemaleParentEntryNo());
                }
            }
            // Check Male Parent
            if (germplasm.getMaleParentEntryNo() != null) {
                if (!germplasmIndexByEntryNo.containsKey(germplasm.getMaleParentEntryNo())) {
                    missingEntryNumbers.add(germplasm.getMaleParentEntryNo());
                }
            }
        }
        if (missingEntryNumbers.size() > 0) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    String.format(missingParentalEntryNoMsg,
                            arrayOfStringFormatter.apply(missingEntryNumbers)));
        }

        if (listNameDup) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, listNameAlreadyExists);
        }
    }

    private BrAPIEntryTypeEnum str_to_test_or_check(String type_str){
        BrAPIEntryTypeEnum type = BrAPIEntryTypeEnum.TEST;
        if("C".equals(type_str)){
            type = BrAPIEntryTypeEnum.CHECK;
        }
        return type;
    }

    @Override
    public Map<String, ImportPreviewStatistics> process(List<BrAPIImport> importRows,
                        Map<Integer, PendingImport> mappedBrAPIImport, Program program, User user, boolean commit) throws ValidatorException {


//        List<Experiment> experiment = (List<Experiment>) importRows;

//        // Method for generating accession number
//        String germplasmSequenceName = program.getGermplasmSequence();
//        if (germplasmSequenceName == null) {
//            log.error(String.format("Program, %s, is missing a value in the germplasm sequence column.", program.getName()));
//            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for germplasm import");
//        }
//        Supplier<BigInteger> nextVal = () -> dsl.nextval(germplasmSequenceName.toLowerCase());

        // Create new objects

        // Assign list name and description
        if (commit) {
            Germplasm germplasm = importRows.get(0).getGermplasm();
            importList = germplasm.constructBrAPIList(program, BRAPI_REFERENCE_SOURCE);
        }

        newExperimentList = new ArrayList<>();
        Map<String, BreedingMethodEntity> breedingMethods = new HashMap<>();
        Boolean nullEntryNotFound = false;
        List<String> badBreedingMethods = new ArrayList<>();
        Map<String, Integer> entryNumberCounts = new HashMap<>();
        List<String> userProvidedEntryNumbers = new ArrayList<>();
        ValidationErrors validationErrors = new ValidationErrors();

//      Read through each row of the input file and populate this.ExperimentList
        for (int i = 0; i < importRows.size(); i++) {
            ImportFileRow fileRow = (ImportFileRow) importRows.get(i);
            populateFileData(fileRow);
        }


        for(ExperimentData experimentData : this.fileData.experimentData()){
            BrAPITrial brAPITrial = new BrAPITrial();
            //Exp Title → Trial.trialName
            brAPITrial.setTrialName( experimentData.getTitle() );
            // Exp Description → Trial.trialDescription
            brAPITrial.setTrialDescription( experimentData.getDescription() );

            //observationUnit →
            for(EnvironmentData environmentData : experimentData.environmentData_values()){
                BrAPIStudy brAPIStudy = new BrAPIStudy();
                // TODO How dose this map?
                // Exp Type → Study.studyType
                // brAPIStudy.setStudyType( environment.);

                // Env → Study.studyName
                brAPIStudy.setStudyName( environmentData.getEnv() );

                // TODO How does this map? brAPIStudy.setSeasons(seasons) wants a List<String> for seasons
                // Env Year → Study.seasons  (This field must be numeric, and be a four digit year)
                //brAPIStudy.setSeasons();

                // Env Location → Study.locationDbId
                brAPIStudy.setLocationDbId( environmentData.getLocation() );
                // TODO lookup by name (getDbid) or else create a new Location

                for(ObservationUnitData observationUnitData : environmentData.getObservationUnitDataList() ){
                    BrAPIObservationUnitLevelRelationship brAPIobsUnitLevel = new BrAPIObservationUnitLevelRelationship();
                    BrAPIObservationUnitPosition brAPIobsUnitPos = new BrAPIObservationUnitPosition();
                    BrAPIObservationUnit brAPIobservationUnit = new BrAPIObservationUnit();
                    brAPIobsUnitPos.setObservationLevel(brAPIobsUnitLevel);
                    brAPIobservationUnit.setObservationUnitPosition(brAPIobsUnitPos);

                    //Test (T) or Check (C) → ObservationUnit.observationUnitPosition.entryType
                    brAPIobsUnitPos.setEntryType( observationUnitData.getTest_or_check() );
                    //Germplasm GID → ObservationUnit.germplasmDbId
                    //TODO This will require looking up the germplasm by external reference to get the correct DBID
                    brAPIobservationUnit.setGermplasmDbId( observationUnitData.getGid() );

                    // Exp Unit → ObservationUnit.observationUnitPosition.observationLevel.levelName
                    brAPIobsUnitLevel.setLevelName( observationUnitData.getExp_unit() );
                    // Exp Unit → Trial.additionalInfo.defaultObservationLevel
                    brAPITrial.putAdditionalInfoItem("defaultObservationLevel", observationUnitData.getExp_unit() );

                    // Exp Unit Id → ObservationUnit.observationUnitName
                    brAPIobservationUnit.setObservationUnitName( observationUnitData.getExp_unit_id() );



                }
            }

        }
//            BrAPIObservationUnitLevelRelationship BrAPIobsUnitLevel = new BrAPIObservationUnitLevelRelationship();
//            BrAPIObservationUnitPosition BrAPIobsUnitPos = new BrAPIObservationUnitPosition();
//            BrAPIObservationUnit BrAPIobservationUnit = new BrAPIObservationUnit();
//            BrAPIobsUnitPos.setObservationLevel(BrAPIobsUnitLevel);
//            BrAPIobservationUnit.setObservationUnitPosition(BrAPIobsUnitPos);


//            //Test (T) or Check (C) → ObservationUnit.observationUnitPosition.entryType
//            String test_or_check = obsUnit.getTest_or_check();
//            BrAPIEntryTypeEnum etype = BrAPIEntryTypeEnum.TEST; //default is TEST
//            if (test_or_check != null && test_or_check.equals("C")) {
//                etype = BrAPIEntryTypeEnum.CHECK;
//            }
//            BrAPIobsUnitPos.setEntryType(etype);

//            //Exp Unit → ObservationUnit.observationUnitPosition.observationLevel.levelName
//            String exp_unit = obsUnit.getExp_unit();
//            BrAPIobsUnitLevel.setLevelName(exp_unit);
//            //Exp Unit → Trial.additionalInfo.defaultObservationLevel
//            brApiTrait.putAdditionalInfoItem("defaultObservationLevel", exp_unit);
//
//            experiment.addEnviroment( obsUnit.getEnv(), obsUnit.getEnv_location(), obsUnit.getEnv_year() );

        //Exp Title → Trial.trialName
//        String exp_title = experiment.getTitle();
//        brApiTrait.setTraitName(exp_title);
//
//        // Exp Description → Trial.trialDescription
//        String exp_description = experiment.getExp_description();
//        brApiTrait.setTraitDescription(exp_description);
//
//        // for each environment
//            // Exp Type → Study.studyType
//            // Env → Study.studyName
//            // Env Year → Study.seasons  (This field must be numeric, and be a four digit year)
//        for (Environment env: experiment.getEnvironments().values()) {
//            BrAPIStudy study = null;
//        }

        Integer newObjectCount;
        Integer ignoredObjectCount;


        // TODO need to add things to newExperimentList
        // Construct our response object
        ImportPreviewStatistics experimentStats = ImportPreviewStatistics.builder()
                .newObjectCount(newExperimentList.size())
                .build();

        //TODO What do we what mapped
        return Map.of(
                "Experiment", experimentStats
        );
    }

    private void populateFileData(ImportFileRow fileRow) {
        ExperimentData experimentData = this.fileData.retrieve_or_add_ExperimentData(fileRow.getExp_title(), fileRow.getExp_description() );
        this.fileData.add_gid( fileRow.getGid() );

        EnvironmentData environmentData = experimentData.retrieve_or_add_environmentData( fileRow.getEnv(), fileRow.getEnv_location(), fileRow.getEnv_year() );

        ObservationUnitData ou = ObservationUnitData.builder()
                .id(                fileRow.getObsUnitID() )
                .germplasm_name(    fileRow.getGermplasmName() )
                .gid(               fileRow.getGid() )
                .exp_unit_id(       fileRow.getExp_unit_id() )
                .exp_replicate_no(  fileRow.getExp_replicate_no() )
                .exp_block_no(      fileRow.getExp_block_no())
                .row(               fileRow.getRow() )
                .column(            fileRow.getColumn() )
                .test_or_check(     this.str_to_test_or_check( fileRow.getTest_or_check() ) )
                .build();
        environmentData.addObservationUnitData( ou );
    }

//            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());
//
//            Germplasm germplasm = brapiImport.getGermplasm();
//
//            // Germplasm
//            if (germplasm != null && germplasm.getGermplasmName() != null) {
//
//                // Get the breeding method database object
//                BreedingMethodEntity breedingMethod = null;
//                if (germplasm.getBreedingMethod() != null) {
//                    if (breedingMethods.containsKey(germplasm.getBreedingMethod())) {
//                        breedingMethod = breedingMethods.get(germplasm.getBreedingMethod());
//                    } else {
//                        List<BreedingMethodEntity> breedingMethodResults = breedingMethodDAO.findByNameOrAbbreviation(germplasm.getBreedingMethod());
//                        if (breedingMethodResults.size() > 0) {
//                            breedingMethods.put(germplasm.getBreedingMethod(), breedingMethodResults.get(0));
//                            breedingMethod = breedingMethods.get(germplasm.getBreedingMethod());
//                        } else {
//                            ValidationError ve = new ValidationError("Breeding Method", badBreedMethodsMsg, HttpStatus.UNPROCESSABLE_ENTITY);
//                            validationErrors.addError(i+2, ve );  // +2 instead of +1 to account for the column header row.
//                            badBreedingMethods.add(germplasm.getBreedingMethod());
//                            breedingMethod = null;
//                        }
//                    }
//                }
//
//                // Assign the entry number
//                if (germplasm.getEntryNo() == null) {
//                    germplasm.setEntryNo(Integer.toString(i + 1));
//                } else {
//                    userProvidedEntryNumbers.add(germplasm.getEntryNo());
//                }
//                entryNumberCounts.put(germplasm.getEntryNo(),
//                        entryNumberCounts.containsKey(germplasm.getEntryNo()) ? entryNumberCounts.get(germplasm.getEntryNo()) + 1 : 1);
//
//                // Create
//                BrAPIGermplasm newGermplasm = germplasm.constructBrAPIGermplasm(program, breedingMethod, user, commit, BRAPI_REFERENCE_SOURCE, nextVal);
//
//                newGermplasmList.add(newGermplasm);
//                // Assign status of the germplasm
//                if (fileGermplasmByName.get(newGermplasm.getDefaultDisplayName()) > 1 || dbGermplasmByName.containsKey(newGermplasm.getDefaultDisplayName())) {
//                    mappedImportRow.setGermplasm(new PendingImportObject<>(ImportObjectState.EXISTING, newGermplasm));
//                } else {
//                    mappedImportRow.setGermplasm(new PendingImportObject<>(ImportObjectState.NEW, newGermplasm));
//                }
//
//                importList.addDataItem(newGermplasm.getGermplasmName());
//            } else {
//                mappedImportRow.setGermplasm(null);
//            }
//            mappedBrAPIImport.put(i, mappedImportRow);
//        }
//        if (validationErrors.hasErrors() ){
//            throw new ValidatorException(validationErrors);
//        }
//
//        // Check for bad breeding methods
//        if (badBreedingMethods.size() > 0) {
//            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
//                    String.format(badBreedMethodsMsg, arrayOfStringFormatter.apply(badBreedingMethods)));
//        }
//
//        // Check for missing entry numbers
//        if (userProvidedEntryNumbers.size() > 0 && userProvidedEntryNumbers.size() < importRows.size()) {
//            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, missingEntryNumbersMsg);
//        }
//
//        // Check for duplicate entry numbers
//        if (entryNumberCounts.size() < importRows.size()) {
//            List<String> dups = entryNumberCounts.keySet().stream()
//                    .filter(key -> entryNumberCounts.get(key) > 1)
//                    .collect(Collectors.toList());
//            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
//                    String.format(duplicateEntryNoMsg, arrayOfStringFormatter.apply(dups)));
//        }
//
//        // Construct pedigree
//        constructPedigreeString(importRows, mappedBrAPIImport, commit);
//
//        // Construct a dependency tree for POSTing order. Dependents on unique germplasm name, (<Name> [<Program Key> - <Accession Number>])
//        if (commit) {
//            createPostOrder();
//        }
//
//        // Construct our response object
//        ImportPreviewStatistics germplasmStats = ImportPreviewStatistics.builder()
//                .newObjectCount(newGermplasmList.size())
//                .build();
//
//        //Modified logic here to check for female parent dbid or entry no, removed check for male due to assumption that shouldn't have only male parent
//        int newObjectCount = newGermplasmList.stream().filter(newGermplasm -> newGermplasm != null).collect(Collectors.toList()).size();
//        ImportPreviewStatistics pedigreeConnectStats = ImportPreviewStatistics.builder()
//                .newObjectCount(importRows.stream().filter(germplasmImport ->
//                        germplasmImport.getGermplasm() != null &&
//                                (germplasmImport.getGermplasm().getFemaleParentDBID() != null || germplasmImport.getGermplasm().getFemaleParentEntryNo() != null)
//                ).collect(Collectors.toList()).size()).build();
//
//        return Map.of(
//                "Germplasm", germplasmStats,
//                "Pedigree Connections", pedigreeConnectStats
//        );

    private void createPostOrder() {
        // Construct a dependency tree for POSTing order
        Set<String> created = existingGermplasms.stream().map(BrAPIGermplasm::getGermplasmName).collect(Collectors.toSet());

        int totalRecorded = 0;

        while (totalRecorded < newExperimentList.size()) {
            List<BrAPIGermplasm> createList = new ArrayList<>();
            for (BrAPIGermplasm germplasm : newExperimentList) {

                // If we've already planned this germplasm, skip
                if (created.contains(germplasm.getGermplasmName())) {
                    continue;
                }

                // If it has no dependencies, add it
                if (germplasm.getPedigree() == null) {
                    createList.add(germplasm);
                    continue;
                }

                // If both parents have been created already, add it
                List<String> pedigreeArray = List.of(germplasm.getPedigree().split("/"));
                String femaleParent = pedigreeArray.get(0);
                String maleParent = pedigreeArray.size() > 1 ? pedigreeArray.get(1) : null;
                if (created.contains(femaleParent)) {
                    if (maleParent == null || created.contains(maleParent)) {
                        createList.add(germplasm);
                    }
                }
            }

            totalRecorded += createList.size();
            if (createList.size() > 0) {
                created.addAll(createList.stream().map(brAPIGermplasm -> brAPIGermplasm.getGermplasmName()).collect(Collectors.toList()));
                postOrder.add(createList);
            } else if (totalRecorded < newExperimentList.size()) {
                // We ran into circular dependencies, throw an error
                throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, circularDependency);
            }
        }
    }

    @Override
    public void validateDependencies(Map<Integer, PendingImport> mappedBrAPIImport) throws ValidatorException {
        // TODO
    }

    @Override
    public void postBrapiData(Map<Integer, PendingImport> mappedBrAPIImport, Program program, ImportUpload upload) {
        // check shared data object for dependency data and update
        updateDependencyValues(mappedBrAPIImport);

        // POST Germplasm
        List<BrAPIGermplasm> createdGermplasm = new ArrayList<>();
        if (newExperimentList.size() > 0) {
            try {
                for (List<BrAPIGermplasm> postGroup: postOrder){
                    createdGermplasm.addAll(brAPIGermplasmService.importBrAPIGermplasm(postGroup, program.getId(), upload));
                }

                // Create germplasm list
                brAPIListDAO.createBrAPILists(List.of(importList), program.getId(), upload);
            } catch (ApiException e) {
                throw new InternalServerException(e.toString(), e);
            }
        }

        // Update our records with what is returned
        Map<String, BrAPIGermplasm> createdGermplasmMap = new HashMap<>();
        createdGermplasm.forEach(germplasm -> {
            createdGermplasmMap.put(germplasm.getGermplasmName(), germplasm);
        });
        for (Map.Entry<Integer,PendingImport> entry : mappedBrAPIImport.entrySet()) {
            String germplasmName = entry.getValue().getGermplasm().getBrAPIObject().getGermplasmName();
            if (createdGermplasmMap.containsKey(germplasmName)) {
                entry.getValue().getGermplasm().setBrAPIObject(createdGermplasmMap.get(germplasmName));
            }
        }




    }

    private void updateDependencyValues(Map<Integer, PendingImport> mappedBrAPIImport) {
        // TODO
    }

    @Override
    public String getName() {
        return NAME;
    }

    public void constructPedigreeString(List<BrAPIImport> importRows, Map<Integer, PendingImport> mappedBrAPIImport, Boolean commit) {

        // Construct pedigree
        // DBID (Acession number) takes precedence over Entry No
        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport brapiImport = importRows.get(i);
            Germplasm germplasm = brapiImport.getGermplasm();

            String femaleParentDB = germplasm.getFemaleParentDBID();
            String maleParentDB = germplasm.getMaleParentDBID();
            String femaleParentFile = germplasm.getFemaleParentEntryNo();
            String maleParentFile = germplasm.getMaleParentEntryNo();

            boolean femaleParentFound = false;
            String pedigreeString = null;
            if (femaleParentDB != null) {
                if (germplasmByAccessionNumber.containsKey(femaleParentDB)) {
                    BrAPIGermplasm femaleParent = germplasmByAccessionNumber.get(femaleParentDB).getBrAPIObject();
                    pedigreeString = commit ? femaleParent.getGermplasmName() : femaleParent.getDefaultDisplayName();
                    femaleParentFound = true;
                }
            } else if (femaleParentFile != null) {
                if (germplasmIndexByEntryNo.containsKey(germplasm.getFemaleParentEntryNo())) {
                    Integer femaleParentInd = germplasmIndexByEntryNo.get(femaleParentFile);
                    BrAPIGermplasm femaleParent = mappedBrAPIImport.get(femaleParentInd).getGermplasm().getBrAPIObject();
                    pedigreeString = commit ? femaleParent.getGermplasmName() : femaleParent.getDefaultDisplayName();
                    femaleParentFound = true;
                }
            }

            if(femaleParentFound) {
                if (maleParentDB != null) {
                    if ((germplasmByAccessionNumber.containsKey(germplasm.getMaleParentDBID()))) {
                        BrAPIGermplasm maleParent = germplasmByAccessionNumber.get(maleParentDB).getBrAPIObject();
                        pedigreeString += String.format("/%s", commit ? maleParent.getGermplasmName() : maleParent.getDefaultDisplayName());
                    }
                } else if (maleParentFile != null){
                    if (germplasmIndexByEntryNo.containsKey(germplasm.getMaleParentEntryNo())) {
                        Integer maleParentInd = germplasmIndexByEntryNo.get(maleParentFile);
                        BrAPIGermplasm maleParent = mappedBrAPIImport.get(maleParentInd).getGermplasm().getBrAPIObject();
                        pedigreeString += String.format("/%s", commit ? maleParent.getGermplasmName() : maleParent.getDefaultDisplayName());
                    }
                }
            }
            mappedBrAPIImport.get(i).getGermplasm().getBrAPIObject().setPedigree(pedigreeString);
        }
    }
}
