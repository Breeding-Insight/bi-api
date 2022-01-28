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
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.daos.BrAPIListDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
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
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class GermplasmProcessor implements Processor {

    private static final String NAME = "Germplasm";
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
    List<BrAPIGermplasm> newGermplasmList;
    List<BrAPIGermplasm> existingGermplasms;
    List<BrAPIGermplasm> existingParentGermplasms;
    List<List<BrAPIGermplasm>> postOrder = new ArrayList<>();
    BrAPIListNewRequest importList = new BrAPIListNewRequest();

    public static String missingParentalDbIdsMsg = "The following parental GIDs were not found in the database: %s.";
    public static String missingParentalEntryNoMsg = "The following parental entry numbers were not found in the database: %s.";
    public static String badBreedMethodsMsg = "Breeding methods not found: %s";
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
    public GermplasmProcessor(BrAPIGermplasmService brAPIGermplasmService, DSLContext dsl, BreedingMethodDAO breedingMethodDAO, BrAPIListDAO brAPIListDAO) {
        this.brAPIGermplasmService = brAPIGermplasmService;
        this.dsl = dsl;
        this.breedingMethodDAO = breedingMethodDAO;
        this.brAPIListDAO = brAPIListDAO;
        this.brAPIGermplasmService = brAPIGermplasmService;
    }

    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) {

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

    @Override
    public Map<String, ImportPreviewStatistics> process(List<BrAPIImport> importRows,
                        Map<Integer, PendingImport> mappedBrAPIImport, Program program, User user, boolean commit) {

        // Method for generating accession number
        String germplasmSequenceName = program.getGermplasmSequence();
        if (germplasmSequenceName == null) {
            log.error(String.format("Program, %s, is missing a value in the germplasm sequence column.", program.getName()));
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for germplasm import");
        }
        Supplier<BigInteger> nextVal = () -> dsl.nextval(germplasmSequenceName.toLowerCase());

        // Create new objects

        // Assign list name and description
        if (commit) {
            Germplasm germplasm = importRows.get(0).getGermplasm();
            importList = germplasm.constructBrAPIList(program, BRAPI_REFERENCE_SOURCE);
        }

        // All rows are considered new germplasm, we don't check for duplicates
        newGermplasmList = new ArrayList<>();
        Map<String, BreedingMethodEntity> breedingMethods = new HashMap<>();
        Boolean nullEntryNotFound = false;
        List<String> badBreedingMethods = new ArrayList<>();
        Map<String, Integer> entryNumberCounts = new HashMap<>();
        List<String> userProvidedEntryNumbers = new ArrayList<>();
        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport brapiImport = importRows.get(i);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());

            Germplasm germplasm = brapiImport.getGermplasm();

            // Germplasm
            if (germplasm != null && germplasm.getGermplasmName() != null) {

                // Get the breeding method database object
                BreedingMethodEntity breedingMethod = null;
                if (germplasm.getBreedingMethod() != null) {
                    if (breedingMethods.containsKey(germplasm.getBreedingMethod())) {
                        breedingMethod = breedingMethods.get(germplasm.getBreedingMethod());
                    } else {
                        List<BreedingMethodEntity> breedingMethodResults = breedingMethodDAO.findByNameOrAbbreviation(germplasm.getBreedingMethod());
                        if (breedingMethodResults.size() > 0) {
                            breedingMethods.put(germplasm.getBreedingMethod(), breedingMethodResults.get(0));
                            breedingMethod = breedingMethods.get(germplasm.getBreedingMethod());
                        } else {
                            badBreedingMethods.add(germplasm.getBreedingMethod());
                            breedingMethod = null;
                        }
                    }
                }

                // Assign the entry number
                if (germplasm.getEntryNo() == null) {
                    germplasm.setEntryNo(Integer.toString(i + 1));
                } else {
                    userProvidedEntryNumbers.add(germplasm.getEntryNo());
                }
                entryNumberCounts.put(germplasm.getEntryNo(),
                        entryNumberCounts.containsKey(germplasm.getEntryNo()) ? entryNumberCounts.get(germplasm.getEntryNo()) + 1 : 1);

                BrAPIGermplasm newGermplasm = germplasm.constructBrAPIGermplasm(program, breedingMethod, user, commit, BRAPI_REFERENCE_SOURCE, nextVal);

                newGermplasmList.add(newGermplasm);
                // Assign status of the germplasm
                if (fileGermplasmByName.get(newGermplasm.getDefaultDisplayName()) > 1 || dbGermplasmByName.containsKey(newGermplasm.getDefaultDisplayName())) {
                    mappedImportRow.setGermplasm(new PendingImportObject<>(ImportObjectState.EXISTING, newGermplasm));
                } else {
                    mappedImportRow.setGermplasm(new PendingImportObject<>(ImportObjectState.NEW, newGermplasm));
                }

                importList.addDataItem(newGermplasm.getGermplasmName());
            } else {
                mappedImportRow.setGermplasm(null);
            }
            mappedBrAPIImport.put(i, mappedImportRow);
        }

        // Check for bad breeding methods
        if (badBreedingMethods.size() > 0) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    String.format(badBreedMethodsMsg, arrayOfStringFormatter.apply(badBreedingMethods)));
        }

        // Check for missing entry numbers
        if (userProvidedEntryNumbers.size() > 0 && userProvidedEntryNumbers.size() < importRows.size()) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, missingEntryNumbersMsg);
        }

        // Check for duplicate entry numbers
        if (entryNumberCounts.size() < importRows.size()) {
            List<String> dups = entryNumberCounts.keySet().stream()
                    .filter(key -> entryNumberCounts.get(key) > 1)
                    .collect(Collectors.toList());
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    String.format(duplicateEntryNoMsg, arrayOfStringFormatter.apply(dups)));
        }

        // Construct pedigree
        constructPedigreeString(importRows, mappedBrAPIImport, commit);

        // Construct a dependency tree for POSTing order. Dependents on unique germplasm name, (<Name> [<Program Key> - <Accession Number>])
        if (commit) {
            createPostOrder();
        }

        // Construct our response object
        ImportPreviewStatistics germplasmStats = ImportPreviewStatistics.builder()
                .newObjectCount(newGermplasmList.size())
                .build();

        //Modified logic here to check for female parent dbid or entry no, removed check for male due to assumption that shouldn't have only male parent
        int newObjectCount = newGermplasmList.stream().filter(newGermplasm -> newGermplasm != null).collect(Collectors.toList()).size();
        ImportPreviewStatistics pedigreeConnectStats = ImportPreviewStatistics.builder()
                .newObjectCount(importRows.stream().filter(germplasmImport ->
                        germplasmImport.getGermplasm() != null &&
                                (germplasmImport.getGermplasm().getFemaleParentDBID() != null || germplasmImport.getGermplasm().getFemaleParentEntryNo() != null)
                ).collect(Collectors.toList()).size()).build();

        return Map.of(
                "Germplasm", germplasmStats,
                "Pedigree Connections", pedigreeConnectStats
        );

    }

    private void createPostOrder() {
        // Construct a dependency tree for POSTing order
        Set<String> created = existingGermplasms.stream().map(BrAPIGermplasm::getGermplasmName).collect(Collectors.toSet());

        int totalRecorded = 0;

        while (totalRecorded < newGermplasmList.size()) {
            List<BrAPIGermplasm> createList = new ArrayList<>();
            for (BrAPIGermplasm germplasm : newGermplasmList) {

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
            } else if (totalRecorded < newGermplasmList.size()) {
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
        if (newGermplasmList.size() > 0) {
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
