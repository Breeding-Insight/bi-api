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
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.api.model.v1.response.ValidationError;
import org.breedinginsight.api.model.v1.response.ValidationErrors;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
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
import org.breedinginsight.dao.db.tables.pojos.ProgramBreedingMethodEntity;
import org.breedinginsight.daos.BreedingMethodDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.ValidatorException;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import tech.tablesaw.api.Table;

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
    private final BreedingMethodDAO breedingMethodDAO;
    private final BrAPIListDAO brAPIListDAO;
    private final DSLContext dsl;
    private final BrAPIGermplasmDAO brAPIGermplasmDAO;

    Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByAccessionNumber = new HashMap<>();
    Map<String, Integer> fileGermplasmByName = new HashMap<>();
    Map<String, BrAPIGermplasm> dbGermplasmByName = new HashMap<>();
    Map<String, BrAPIGermplasm> dbGermplasmByAccessionNo = new HashMap<>();
    Map<String, Integer> germplasmIndexByEntryNo = new HashMap<>();
    List<BrAPIGermplasm> newGermplasmList;

    List<BrAPIGermplasm> updatedGermplasmList;
    List<BrAPIGermplasm> existingGermplasms;
    List<List<BrAPIGermplasm>> postOrder = new ArrayList<>();
    BrAPIListNewRequest importList = new BrAPIListNewRequest();

    public static String missingDbIdsMsg = "The following GIDs were not found in the database: %s.";
    public static String missingParentalDbIdsMsg = "The following parental GIDs were not found in the database: %s.";
    public static String missingParentalEntryNoMsg = "The following parental entry numbers were not found in the database: %s.";
    public static String badBreedMethodsMsg = "Invalid breeding method";
    public static String missingEntryNumbersMsg = "Either all or none of the germplasm must have entry numbers.";
    public static String duplicateEntryNoMsg = "Entry numbers must be unique. Duplicated entry numbers found: %s";
    public static String circularDependency = "Circular dependency in the pedigree tree";
    public static String listNameAlreadyExists = "Import group name already exists";
    public static String missingGID = "No germplasm of GID %s was found in the database";
    public static String pedigreeAlreadyExists = "Pedigree information cannot be overwritten";
    public static String missingFemaleParent = "Female parent is missing.  If the female parent is unknown, specify GID or entry number 0 as the female parent";

    public static Function<List<String>, String> arrayOfStringFormatter = (lst) -> {
        List<String> lstCopy = new ArrayList<>(lst);
        Collections.sort(lstCopy);
        return StringUtils.join(lstCopy, ", ");
    };

    @Inject
    public GermplasmProcessor(BrAPIGermplasmService brAPIGermplasmService, DSLContext dsl, BreedingMethodDAO breedingMethodDAO, BrAPIListDAO brAPIListDAO, BrAPIGermplasmDAO brAPIGermplasmDAO) {
        this.brAPIGermplasmService = brAPIGermplasmService;
        this.dsl = dsl;
        this.breedingMethodDAO = breedingMethodDAO;
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.brAPIListDAO = brAPIListDAO;
        this.brAPIGermplasmService = brAPIGermplasmService;
    }

    public void getExistingBrapiData(List<BrAPIImport> importRows, Program program) throws ApiException {

        // Get all of our objects specified in the data file by their unique attributes
        Map<String, Boolean> germplasmDBIDs = new HashMap<>();
        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport germplasmImport = importRows.get(i);
            if (germplasmImport.getGermplasm() != null) {

                // Retrieve parent dbids to assess if already in db
                if (germplasmImport.getGermplasm().getFemaleParentDBID() != null) {
                    germplasmDBIDs.put(germplasmImport.getGermplasm().getFemaleParentDBID(), true);
                }
                if (germplasmImport.getGermplasm().getMaleParentDBID() != null) {
                    germplasmDBIDs.put(germplasmImport.getGermplasm().getMaleParentDBID(), true);
                }
                if (germplasmImport.getGermplasm().getAccessionNumber() != null) {
                    germplasmDBIDs.put(germplasmImport.getGermplasm().getAccessionNumber(), false);
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
        List<String> missingParentalDbIds = germplasmDBIDs.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toList());
        List<String> missingDbIds = germplasmDBIDs.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).collect(Collectors.toList());
        if (germplasmDBIDs.size() > 0) {
            try {
                existingGermplasms = brAPIGermplasmService.getRawGermplasmByAccessionNumber(new ArrayList<>(germplasmDBIDs.keySet()), program.getId());
                List<String> existingDbIds = existingGermplasms.stream()
                                                                     .map(germplasm -> germplasm.getAccessionNumber())
                                                                     .collect(Collectors.toList());
                missingParentalDbIds.removeAll(existingDbIds);
                missingDbIds.removeAll(existingDbIds);

                existingGermplasms.forEach(existingGermplasm -> {
                    germplasmByAccessionNumber.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm));
                });
            } catch (ApiException e) {
                // We shouldn't get an error back from our services. If we do, nothing the user can do about it
                throw new InternalServerException(e.toString(), e);
            }
        }

        // Get existing germplasm names
        List<BrAPIGermplasm> dbGermplasm = brAPIGermplasmService.getGermplasmByDisplayName(new ArrayList<>(fileGermplasmByName.keySet()), program.getId());
        dbGermplasm.forEach(germplasm -> {
            dbGermplasmByName.put(germplasm.getDefaultDisplayName(), germplasm);
            dbGermplasmByAccessionNo.put(germplasm.getAccessionNumber(), germplasm);
        });

        // Check for existing germplasm lists
        Boolean listNameDup = false;
        if (importRows.size() > 0 && importRows.get(0).getGermplasm().getListName() != null) {
            try {
                Germplasm row = importRows.get(0).getGermplasm();
                String listName = Germplasm.constructGermplasmListName(row.getListName(), program);
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

        //Remove id indicating unknown parent
        missingParentalDbIds.remove("0");
        missingDbIds.remove("0");

        // Parent reference checks
        if (missingParentalDbIds.size() > 0) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                          String.format(missingParentalDbIdsMsg,
                                                        arrayOfStringFormatter.apply(missingParentalDbIds)));
        }

        //GID existence check
        if (missingDbIds.size() > 0) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                          String.format(missingDbIdsMsg,
                                                        arrayOfStringFormatter.apply(missingDbIds)));
        }

        List<String> missingEntryNumbers = new ArrayList<>();
        for (BrAPIImport importRow: importRows) {
            Germplasm germplasm = importRow.getGermplasm();
            // Check Female Parent
            if (germplasm.getFemaleParentEntryNo() != null) {
                if ((!germplasmIndexByEntryNo.containsKey(germplasm.getFemaleParentEntryNo())) && !(germplasm.getFemaleParentEntryNo().equals("0"))) {
                    missingEntryNumbers.add(germplasm.getFemaleParentEntryNo());
                }
            }
            // Check Male Parent
            if (germplasm.getMaleParentEntryNo() != null) {
                if ((!germplasmIndexByEntryNo.containsKey(germplasm.getMaleParentEntryNo())) && !(germplasm.getMaleParentEntryNo().equals("0"))) {
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
    public Map<String, ImportPreviewStatistics> process(ImportUpload upload, List<BrAPIImport> importRows,
                                                        Map<Integer, PendingImport> mappedBrAPIImport, Table data,
                                                        Program program, User user, boolean commit) throws ValidatorException {

        log.debug("Processing germplasm import");

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

        newGermplasmList = new ArrayList<>();
        updatedGermplasmList = new ArrayList<>();
        Map<String, ProgramBreedingMethodEntity> breedingMethods = new HashMap<>();
        Boolean nullEntryNotFound = false;
        List<String> badBreedingMethods = new ArrayList<>();
        Map<String, Integer> entryNumberCounts = new HashMap<>();
        List<String> userProvidedEntryNumbers = new ArrayList<>();
        ValidationErrors validationErrors = new ValidationErrors();
        for (int i = 0; i < importRows.size(); i++) {
            log.debug("processing germplasm row: " + (i+1));
            BrAPIImport brapiImport = importRows.get(i);
            PendingImport mappedImportRow = mappedBrAPIImport.getOrDefault(i, new PendingImport());

            Germplasm germplasm = brapiImport.getGermplasm();

            // Assign the entry number
            if (germplasm.getEntryNo() == null) {
                germplasm.setEntryNo(Integer.toString(i + 1));
            } else {
                userProvidedEntryNumbers.add(germplasm.getEntryNo());
            }
            entryNumberCounts.put(germplasm.getEntryNo(),
                    entryNumberCounts.containsKey(germplasm.getEntryNo()) ? entryNumberCounts.get(germplasm.getEntryNo()) + 1 : 1);

            UUID importListId = brAPIGermplasmService.getGermplasmListId(importList);

            // Germplasm

            //todo double check what dbgermplasmbyaccessionNo actually getting
            //TODO maybe make separate method for cleanliness
            // Have GID so updating an existing germplasm record
            if (germplasm.getAccessionNumber() != null) {
                processExistingGermplasm(germplasm, validationErrors, importRows, program, importListId, commit, mappedImportRow, i);
            } else {
                processNewGermplasm(germplasm, validationErrors, breedingMethods, badBreedingMethods, program, importListId, commit, mappedImportRow, i, user, nextVal);
            }
            mappedBrAPIImport.put(i, mappedImportRow);
        }
        if (validationErrors.hasErrors() ){
            throw new ValidatorException(validationErrors);
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
        return getStatisticsMap(importRows);
    }

    private void processNewGermplasm(Germplasm germplasm, ValidationErrors validationErrors, Map<String, ProgramBreedingMethodEntity> breedingMethods,
                                     List<String> badBreedingMethods,
                                     Program program, UUID importListId, boolean commit, PendingImport mappedImportRow, int i, User user, Supplier<BigInteger> nextVal) {
        // Get the breeding method database object
        ProgramBreedingMethodEntity breedingMethod = null;
        if (germplasm.getBreedingMethod() != null) {
            if (breedingMethods.containsKey(germplasm.getBreedingMethod())) {
                breedingMethod = breedingMethods.get(germplasm.getBreedingMethod());
            } else {
                List<ProgramBreedingMethodEntity> breedingMethodResults = breedingMethodDAO.findByNameOrAbbreviation(germplasm.getBreedingMethod(), program.getId());
                if (breedingMethodResults.size() > 0) {
                    breedingMethods.put(germplasm.getBreedingMethod(), breedingMethodResults.get(0));
                    breedingMethod = breedingMethods.get(germplasm.getBreedingMethod());
                } else {
                    ValidationError ve = new ValidationError("Breeding Method", badBreedMethodsMsg, HttpStatus.UNPROCESSABLE_ENTITY);
                    validationErrors.addError(i + 2, ve);  // +2 instead of +1 to account for the column header row.
                    badBreedingMethods.add(germplasm.getBreedingMethod());
                }
            }
        }

        validatePedigree(germplasm, i + 2, validationErrors);

        BrAPIGermplasm newGermplasm = germplasm.constructBrAPIGermplasm(program, breedingMethod, user, commit, BRAPI_REFERENCE_SOURCE, nextVal, importListId);

        newGermplasmList.add(newGermplasm);
        // Assign status of the germplasm
        if (fileGermplasmByName.get(newGermplasm.getDefaultDisplayName()) > 1 || dbGermplasmByName.containsKey(newGermplasm.getDefaultDisplayName())) {
            mappedImportRow.setGermplasm(new PendingImportObject<>(ImportObjectState.EXISTING, newGermplasm));
        } else {
            mappedImportRow.setGermplasm(new PendingImportObject<>(ImportObjectState.NEW, newGermplasm));
        }

        importList.addDataItem(newGermplasm.getGermplasmName());
    }

    private boolean processExistingGermplasm(Germplasm germplasm, ValidationErrors validationErrors, List<BrAPIImport> importRows, Program program, UUID importListId, boolean commit, PendingImport mappedImportRow, int rowIndex) {
        BrAPIGermplasm existingGermplasm;
        String gid = germplasm.getAccessionNumber();
        if (germplasmByAccessionNumber.containsKey(gid)) {
            existingGermplasm = germplasmByAccessionNumber.get(gid).getBrAPIObject();
        } else {
            //should be caught in getExistingBrapiData
            ValidationError ve = new ValidationError("GID", String.format(missingGID, gid), HttpStatus.NOT_FOUND);
            validationErrors.addError(rowIndex+2, ve );  // +2 instead of +1 to account for the column header row.
            return false;
        }

        // Error conditions:
        // has existing pedigree and file pedigree is different and not empty
        // Valid conditions:
        // no existing pedigree and file different pedigree
        // existing pedigree and file pedigree same
        // existing pedigree and file pedigree empty
        if(hasPedigree(existingGermplasm) && germplasm.pedigreeExists()) {
            if(!arePedigreesEqual(existingGermplasm, germplasm, importRows)) {
                ValidationError ve = new ValidationError("Pedigree", pedigreeAlreadyExists, HttpStatus.UNPROCESSABLE_ENTITY);
                validationErrors.addError(rowIndex + 2, ve);  // +2 instead of +1 to account for the column header row.
                return false;
            }
        }

        if(germplasm.pedigreeExists()) {
            validatePedigree(germplasm, rowIndex + 2, validationErrors);
        }

        germplasm.updateBrAPIGermplasm(existingGermplasm, program, importListId, commit, true);

        updatedGermplasmList.add(existingGermplasm);
        mappedImportRow.setGermplasm(new PendingImportObject<>(ImportObjectState.MUTATED, existingGermplasm));
        importList.addDataItem(existingGermplasm.getGermplasmName());


        return true;
    }

    private boolean canUpdatePedigree(BrAPIGermplasm existingGermplasm, Germplasm germplasm) {
        return !hasPedigreeString(existingGermplasm) && germplasm.pedigreeExists();
    }

    private boolean hasPedigreeString(BrAPIGermplasm germplasm) {
        return StringUtils.isNotBlank(germplasm.getPedigree());
    }

    private boolean hasPedigree(BrAPIGermplasm germplasm) {
        return StringUtils.isNotBlank(germplasm.getPedigree())
                || germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_GID)
                || germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID)
                || (germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN) &&
                    germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN).getAsBoolean())
                || (germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN) &&
                    germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN).getAsBoolean());
    }

    /**
     * Compare an existing germplasm's pedigree to the incoming germplasm's pedigree to ensure they are the same.<br><br>
     * Assumes that an empty value for a given parent in the incoming germplasm is equal to the existing germplasm.<br><br>
     * Assumes that the existing germplasm has pedigree
     * @param existingGermplasm the existing germplasm with pedigree
     * @param germplasm the germplasm record coming in the file
     * @param importRows all records coming in the file.  Needed to look up GID of a parent referenced by entry number in the file
     * @return true if the two germplasm pedigrees are effectively equal, false otherwise
     */
    private boolean arePedigreesEqual(BrAPIGermplasm existingGermplasm, Germplasm germplasm, List<BrAPIImport> importRows) {
        if(germplasm.pedigreeExists()) {
            StringBuilder existingPedigreeGIDString = new StringBuilder();
            String existingFemalePedigree = getParentId(existingGermplasm, existingPedigreeGIDString, BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_GID, BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN);
            existingPedigreeGIDString.append("/");
            String existingMalePedigree = getParentId(existingGermplasm, existingPedigreeGIDString, BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID, BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN);

            StringBuilder germplasmPedigreeGIDString = new StringBuilder();
            if (StringUtils.isNotBlank(germplasm.getFemaleParentDBID())) {
                germplasmPedigreeGIDString.append(germplasm.getFemaleParentDBID());
            } else if (StringUtils.isNotBlank(germplasm.getFemaleParentEntryNo())) {
                Integer femaleParentIdx = germplasmIndexByEntryNo.get(germplasm.getFemaleParentEntryNo());
                BrAPIImport femaleParentRow = importRows.get(femaleParentIdx);
                BrAPIGermplasm femaleGerm = dbGermplasmByName.get(femaleParentRow.getGermplasm()
                                                                                 .getGermplasmName());
                if (femaleGerm != null) {
                    germplasmPedigreeGIDString.append(femaleGerm.getAccessionNumber());
                } else {
                    germplasmPedigreeGIDString.append("-1");
                }
            } else {
                germplasmPedigreeGIDString.append(existingFemalePedigree);
            }
            germplasmPedigreeGIDString.append("/");
            if (StringUtils.isNotBlank(germplasm.getMaleParentDBID())) {
                germplasmPedigreeGIDString.append(germplasm.getMaleParentDBID());
            } else if (StringUtils.isNotBlank(germplasm.getMaleParentEntryNo())) {
                Integer maleParentIdx = germplasmIndexByEntryNo.get(germplasm.getMaleParentEntryNo());
                BrAPIImport maleParentRow = importRows.get(maleParentIdx);
                BrAPIGermplasm maleGerm = dbGermplasmByName.get(maleParentRow.getGermplasm()
                                                                             .getGermplasmName());
                if (maleGerm != null) {
                    germplasmPedigreeGIDString.append(maleGerm.getAccessionNumber());
                } else {
                    germplasmPedigreeGIDString.append("-1");
                }
            } else {
                germplasmPedigreeGIDString.append(existingMalePedigree);
            }

            return existingPedigreeGIDString.toString().equals(germplasmPedigreeGIDString.toString());
        } else {
            return true;
        }
    }

    private String getParentId(BrAPIGermplasm existingGermplasm, StringBuilder pedigreeGIDString, String gidAdditionaInfoField, String unknownAdditionalInfoField) {
        if (existingGermplasm.getAdditionalInfo()
                             .has(gidAdditionaInfoField)) {
            pedigreeGIDString.append(existingGermplasm.getAdditionalInfo()
                                                              .get(gidAdditionaInfoField)
                                                              .getAsString());
            return existingGermplasm.getAdditionalInfo()
                                                      .get(gidAdditionaInfoField)
                                                      .getAsString();
        } else if (existingGermplasm.getAdditionalInfo()
                                    .has(unknownAdditionalInfoField) && existingGermplasm.getAdditionalInfo()
                                                                                                              .get(unknownAdditionalInfoField)
                                                                                                              .getAsBoolean()) {
            pedigreeGIDString.append("0");
            return "0";
        }
        return "";
    }

    private boolean canUpdatePedigreeNoEqualsCheck(BrAPIGermplasm existingGermplasm, Germplasm germplasm) {


        return StringUtils.isBlank(existingGermplasm.getPedigree()) &&
                germplasm.pedigreeExists();
    }

    private Map<String, ImportPreviewStatistics> getStatisticsMap(List<BrAPIImport> importRows) {

        ImportPreviewStatistics germplasmStats = ImportPreviewStatistics.builder()
                .newObjectCount(newGermplasmList.size())
                .ignoredObjectCount(germplasmByAccessionNumber.size())
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

    private void validatePedigree(Germplasm germplasm, Integer rowNumber, ValidationErrors validationErrors) {
        String femaleParentEntryNo = germplasm.getFemaleParentEntryNo();
        String maleParentEntryNo = germplasm.getMaleParentEntryNo();
        String femaleParentGID = germplasm.getFemaleParentDBID();
        String maleParentGID = germplasm.getMaleParentDBID();

        if(StringUtils.isNotBlank(maleParentEntryNo) && StringUtils.isBlank(femaleParentEntryNo) && StringUtils.isBlank(femaleParentGID)) {
            validationErrors.addError(rowNumber, new ValidationError("Male Parent Entry No", missingFemaleParent, HttpStatus.UNPROCESSABLE_ENTITY));
        } else if(StringUtils.isNotBlank(maleParentGID) && StringUtils.isBlank(femaleParentEntryNo) && StringUtils.isBlank(femaleParentGID)) {
            validationErrors.addError(rowNumber, new ValidationError("Male Parent GID", missingFemaleParent, HttpStatus.UNPROCESSABLE_ENTITY));
        }
    }

    private void createPostOrder() {
        // Construct a dependency tree for POSTing order
        Set<String> created = existingGermplasms.stream().map(BrAPIGermplasm::getGermplasmName).collect(Collectors.toSet());

        //todo this gets messy

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
                if (created.contains(femaleParent) || germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN).getAsBoolean()) {
                    if (maleParent == null || created.contains(maleParent) || germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN).getAsBoolean()) {
                        createList.add(germplasm);
                    }
                }
            }

            totalRecorded += createList.size();
            if (createList.size() > 0) {
                created.addAll(createList.stream().map(BrAPIGermplasm::getGermplasmName).collect(Collectors.toList()));
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

        if (!newGermplasmList.isEmpty()) {
            for (List<BrAPIGermplasm> postGroup: postOrder){
                createdGermplasm.addAll(brAPIGermplasmService.createBrAPIGermplasm(postGroup, program.getId(), upload));
            }
        }

        // PUT germplasm
        if (!updatedGermplasmList.isEmpty()) {
            brAPIGermplasmService.updateBrAPIGermplasm(updatedGermplasmList, program.getId(), upload);
        }

        // Create list
        if (!newGermplasmList.isEmpty() || !updatedGermplasmList.isEmpty()) {
            try {
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

        log.debug("constructing pedigree strings");
        // Construct pedigree
        // DBID (Acession number) takes precedence over Entry No
        for (int i = 0; i < importRows.size(); i++) {
            BrAPIImport brapiImport = importRows.get(i);
            Germplasm germplasm = brapiImport.getGermplasm();

            String femaleParentDB = germplasm.getFemaleParentDBID();
            String maleParentDB = germplasm.getMaleParentDBID();
            String femaleParentFile = germplasm.getFemaleParentEntryNo();
            String maleParentFile = germplasm.getMaleParentEntryNo();

            boolean femaleParentUnknown = false;
            boolean maleParentUnknown = false;

            BrAPIGermplasm femaleParent = null;
            BrAPIGermplasm maleParent = null;

            boolean femaleParentFound = false;
            StringBuilder pedigreeString = new StringBuilder();
            if (femaleParentDB != null) {
                if (femaleParentDB.equals("0")) {
                    femaleParentUnknown = true;
                } else if (germplasmByAccessionNumber.containsKey(femaleParentDB)) {
                    femaleParent = germplasmByAccessionNumber.get(femaleParentDB).getBrAPIObject();
                    pedigreeString.append(commit ? femaleParent.getGermplasmName() : femaleParent.getDefaultDisplayName());
                    femaleParentFound = true;
                }
            } else if (femaleParentFile != null) {
                if (femaleParentFile.equals("0")){
                    femaleParentUnknown = true;
                }
                else if (germplasmIndexByEntryNo.containsKey(germplasm.getFemaleParentEntryNo())) {
                    Integer femaleParentInd = germplasmIndexByEntryNo.get(femaleParentFile);
                    femaleParent = mappedBrAPIImport.get(femaleParentInd).getGermplasm().getBrAPIObject();
                    pedigreeString.append(commit ? femaleParent.getGermplasmName() : femaleParent.getDefaultDisplayName());
                    femaleParentFound = true;
                }
            }

            if(femaleParentFound || femaleParentUnknown) {
                if (maleParentDB != null) {
                    if (maleParentDB.equals("0")) {
                        maleParentUnknown = true;
                    }
                    if ((germplasmByAccessionNumber.containsKey(germplasm.getMaleParentDBID()))) {
                        maleParent = germplasmByAccessionNumber.get(maleParentDB).getBrAPIObject();
                        pedigreeString.append(String.format("/%s", commit ? maleParent.getGermplasmName() : maleParent.getDefaultDisplayName()));
                    }
                } else if (maleParentFile != null){
                    if (maleParentFile.equals("0")) {
                        maleParentUnknown = true;
                    }
                    if (germplasmIndexByEntryNo.containsKey(germplasm.getMaleParentEntryNo())) {
                        Integer maleParentInd = germplasmIndexByEntryNo.get(maleParentFile);
                        maleParent = mappedBrAPIImport.get(maleParentInd).getGermplasm().getBrAPIObject();
                        pedigreeString.append(String.format("/%s", commit ? maleParent.getGermplasmName() : maleParent.getDefaultDisplayName()));
                    }
                }
            }

            BrAPIGermplasm brAPIGermplasm = mappedBrAPIImport.get(i).getGermplasm().getBrAPIObject();

            // only allow this when not committing so that display name version can be shown in preview
            if (!commit) {
                if (brAPIGermplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_PEDIGREE_BY_NAME)) {
                    brAPIGermplasm.setPedigree(brAPIGermplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_PEDIGREE_BY_NAME).getAsString());
                }

            }

            // no existing pedigree and pedigree not empty
            // pedigrees will be equal at this point from prior processing code if being updated so don't check that
            if (canUpdatePedigree(brAPIGermplasm, germplasm)) {

                brAPIGermplasm.setPedigree(pedigreeString.length() > 0 ? pedigreeString.toString() : null);
                //Simpler to just always add boolean, but consider for logic that previous imported values won't have that additional info value
                brAPIGermplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN, femaleParentUnknown);
                brAPIGermplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN, maleParentUnknown);

                if (commit) {
                    if (femaleParentFound) {
                        brAPIGermplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_GID, femaleParent.getAccessionNumber());
                        // Add femaleParentUUID to additionalInfo.
                        Optional<BrAPIExternalReference> femaleParentUUID = Utilities.getExternalReference(femaleParent.getExternalReferences(), BRAPI_REFERENCE_SOURCE);
                        if (femaleParentUUID.isPresent()) {
                            brAPIGermplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_UUID, femaleParentUUID.get().getReferenceID());
                        }
                    }

                    if (maleParent != null) {
                        brAPIGermplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID, maleParent.getAccessionNumber());
                        // Add maleParentUUID to additionalInfo.
                        Optional<BrAPIExternalReference> maleParentUUID = Utilities.getExternalReference(maleParent.getExternalReferences(), BRAPI_REFERENCE_SOURCE);
                        if (maleParentUUID.isPresent()) {
                            brAPIGermplasm.putAdditionalInfoItem(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_UUID, maleParentUUID.get().getReferenceID());
                        }                    }
                }
            }
        }
    }
}
