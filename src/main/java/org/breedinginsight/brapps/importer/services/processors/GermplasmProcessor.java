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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.daos.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.base.Germplasm;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.dao.db.tables.daos.BreedingMethodDao;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Prototype
public class GermplasmProcessor implements Processor {

    private static final String NAME = "Germplasm";
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    private BrAPIGermplasmDAO brAPIGermplasmDAO;
    private BreedingMethodDAO breedingMethodDAO;
    private DSLContext dsl;

    Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByAccessionNumber = new HashMap<>();
    Map<String, String> germplasmNameByEntryNo = new HashMap<>();
    List<BrAPIGermplasm> newGermplasmList;
    List<BrAPIGermplasm> existingGermplasms;
    List<BrAPIGermplasm> existingParentGermplasms;
    List<List<BrAPIGermplasm>> postOrder = new ArrayList<>();

    @Inject
    public GermplasmProcessor(BrAPIGermplasmDAO brAPIGermplasmDAO, DSLContext dsl, BreedingMethodDAO breedingMethodDAO) {
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.dsl = dsl;
        this.breedingMethodDAO = breedingMethodDAO;
    }

    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) {

        // Get all of our objects specified in the data file by their unique attributes
        Set<String> germplasmNames = new HashSet<>();
        Set<String> germplasmDBIDs = new HashSet<>();
        for (BrAPIImport germplasmImport : importRows) {
            if (germplasmImport.getGermplasm() != null) {
                // Retrieve names to assess if rows already in db
                if (germplasmImport.getGermplasm().getGermplasmName() != null) {
                    germplasmNames.add(germplasmImport.getGermplasm().getGermplasmName());
                }

                // Retrieve parent dbids to assess if already in db
                if (germplasmImport.getGermplasm().getFemaleParentDBID() != null) {
                    germplasmDBIDs.add(germplasmImport.getGermplasm().getFemaleParentDBID());
                }
                if (germplasmImport.getGermplasm().getMaleParentDBID() != null) {
                    germplasmDBIDs.add(germplasmImport.getGermplasm().getMaleParentDBID());
                }

                //Retrieve entry numbers of file for comparison with parent entry numbers
                if (germplasmImport.getGermplasm().getEntryNo()!= null) {
                    germplasmNameByEntryNo.put(germplasmImport.getGermplasm().getEntryNo(), germplasmImport.getGermplasm().getGermplasmName());
                }

            }
        }

        // If parental DBID, should also be in database
        existingGermplasms = new ArrayList<>();
        if (germplasmDBIDs.size() > 0) {
            try {
                existingParentGermplasms = brAPIGermplasmDAO.getGermplasmByAccessionNumber(new ArrayList<>(germplasmDBIDs), program.getId());
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
    }

    @Override
    public Map<String, ImportPreviewStatistics> process(List<BrAPIImport> importRows, Map<Integer, PendingImport> mappedBrAPIImport, Program program, boolean commit) {

        // TODO: Set pedigree string to parents germplasm names on commit
        // TODO: Check other fields are populated in the preview method

        // Method for generating accession number
        String germplasmSequenceName = program.getGermplasmSequence();
        if (germplasmSequenceName == null) {
            log.error(String.format("Program, %s, is missing a value in the germplasm sequence column.", program.getName()));
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Program is not properly configured for germplasm import");
        }
        Supplier<BigInteger> nextVal = () -> dsl.nextval(program.getGermplasmSequence());

        // Create new objects
        // All rows are considered new germplasm, we don't check for duplicates
        newGermplasmList = new ArrayList<>();
        Map<String, BreedingMethodEntity> breedingMethods = new HashMap<>();
        Boolean nullEntryNotFound = false;
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
                            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                    String.format("Breeding method, %s, not found", germplasm.getBreedingMethod()));
                        }
                    }
                }

                // Assign the entry number
                if (germplasm.getEntryNo() == null) {
                    germplasm.setEntryNo(Integer.toString(i + 1));
                    nullEntryNotFound = true;
                } else if (germplasm.getEntryNo() != null && nullEntryNotFound) {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            String.format("Either all or none of the germplasm must have entry numbers.", germplasm.getBreedingMethod()));
                }

                BrAPIGermplasm newGermplasm = germplasm.constructBrAPIGermplasm(program, breedingMethod, commit, BRAPI_REFERENCE_SOURCE, nextVal);

                // Check the parents exist
                // DBID takes precedence over Entry No
                // For the moment, constructs pedigree string with displayName if only parent Entry No
                String femaleParentDB = germplasm.getFemaleParentDBID() != null ? germplasm.getFemaleParentDBID() : null;
                String maleParentDB = germplasm.getMaleParentDBID() != null ? germplasm.getMaleParentDBID() : null;
                String femaleParentFile = germplasm.getFemaleParentEntryNo() != null ? germplasm.getFemaleParentEntryNo() : null;
                String maleParentFile = germplasm.getMaleParentEntryNo() != null ? germplasm.getMaleParentEntryNo() : null;
                boolean femaleParentFound = false;
                String pedigreeString = null;
                if (femaleParentDB != null) {
                    if (germplasmByAccessionNumber.containsKey(germplasm.getFemaleParentDBID())) {
                        pedigreeString = germplasmByAccessionNumber.get(femaleParentDB).getBrAPIObject().getGermplasmName();
                        femaleParentFound = true;
                    }
                } else if (femaleParentFile != null) {
                    if (germplasmNameByEntryNo.containsKey(germplasm.getFemaleParentEntryNo())) {
                        pedigreeString = germplasmNameByEntryNo.get(femaleParentFile);
                        femaleParentFound = true;
                    }
                }

                if(femaleParentFound) {
                    if (maleParentDB != null) {
                        if ((germplasmByAccessionNumber.containsKey(germplasm.getMaleParentDBID()))) {
                            pedigreeString += "/" + germplasmByAccessionNumber.get(maleParentDB).getBrAPIObject().getGermplasmName();
                        }
                    } else if (maleParentFile != null){
                        if (germplasmNameByEntryNo.containsKey(germplasm.getMaleParentEntryNo())) {
                            pedigreeString += "/" + germplasmNameByEntryNo.get(maleParentFile);
                        }
                    }
                    newGermplasm.setPedigree(pedigreeString);
                }

                newGermplasmList.add(newGermplasm);
                mappedImportRow.setGermplasm(new PendingImportObject<>(ImportObjectState.NEW, newGermplasm));
            } else {
                mappedImportRow.setGermplasm(null);
            }
            mappedBrAPIImport.put(i, mappedImportRow);
        }

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
                throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Circular dependency in the pedigree tree");
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
                    createdGermplasm.addAll(brAPIGermplasmDAO.createBrAPIGermplasm(postGroup, program.getId(), upload));
                }
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

}
