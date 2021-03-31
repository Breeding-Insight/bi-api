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

package org.breedinginsight.brapps.importer.model.imports;

import com.ibm.icu.text.ArabicShaping;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.CrossesApi;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.client.v2.modules.germplasm.GermplasmAttributeValuesApi;
import org.brapi.client.v2.modules.germplasm.GermplasmAttributesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.germ.*;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.BrAPIObservationUnitHierarchyLevel;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.breedinginsight.api.deserializer.ArrayOfStringDeserializer;
import org.breedinginsight.brapps.importer.model.base.*;
import org.breedinginsight.brapps.importer.model.config.ImportRelation;
import org.breedinginsight.brapps.importer.model.config.ImportRelationType;
import org.breedinginsight.brapps.importer.services.BrAPIFileImportService;
import org.breedinginsight.brapps.importer.services.BrAPIQueryService;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.management.relation.RelationType;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class PedigreeImportService extends BrAPIImportService {

    private String ID = "PedigreeImport";

    private BrAPIQueryService brAPIQueryService;
    private BrAPIProvider brAPIProvider;

    @Inject
    public PedigreeImportService(BrAPIQueryService brAPIQueryService, BrAPIProvider brAPIProvider)
    {
        this.brAPIQueryService = brAPIQueryService;
        this.brAPIProvider = brAPIProvider;
    }

    @Override
    public PedigreeImport getImportClass() {
        return new PedigreeImport();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void process(List<BrAPIImport> brAPIImports, Table data, Program program) throws UnprocessableEntityException {

        // TODO: Tablesaw is formatting our numbers weird -> This card
        // TODO: Need to check relationships before we POST any objects. -> This card

        // TODO: Later items
        // TODO: Need to have a discussion on whether we only create new things, or if we update too. Example: Germplasm attributes, crosses
        // TODO: How do we handle duplicates? Some can be ignore. What if germplasm have the same name, but different attributes? Throw error for now.
        // TODO: Add enum type for value (dropdown on UI) -> New card
        // TODO: Need to get the whole file to do a legit upload -> Need to wait until POST/GET pattern for file is made (Next card)
        // TODO: Make an @ImportIgnore annotation to be able to ignore certain fields
        // TODO: See if we can reduce the number of loops
        // TODO: Construct all the objects at the same time and set references later so that we can report the preview back

        List<PedigreeImport> pedigreeImports = (List<PedigreeImport>)(List<?>) brAPIImports;

        // Get BrAPI Program
        BrAPIProgram brAPIProgram;
        try {
            Optional<BrAPIProgram> optionalBrAPIProgram = brAPIQueryService.getProgram(program.getId());
            if (optionalBrAPIProgram.isEmpty()){
                throw new ApiException("Program was not found in the brapi service");
            }
            brAPIProgram = optionalBrAPIProgram.get();
        } catch (ApiException e) {
            // Our program should be set up already
            throw new InternalServerException(e.toString(), e);
        }

        // Get all of our objects specified by their unique attributes
        // Pair are used for objects where we need to reference back to the original import object
        Set<String> germplasmNames = new HashSet<>();
        //TODO: See if sets work for pairs
        Set<Pair<String, String>> ouNameByStudy = new HashSet<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);

            // Observation units
            if (pedigreeImport.getObservationUnit() != null) {
                ouNameByStudy.add(new MutablePair<>(pedigreeImport.getObservationUnit().getObservationUnitName(), null));
            }
        }

        // Check existing objects
        Map<String, BrAPIGermplasm> existingGermplasmMap = new HashMap<>();
        Map<String, BrAPIObservationUnit> existingOUMap = new HashMap<>();
        try {
            List<BrAPIGermplasm> existingGermplasms = brAPIQueryService.getGermplasmByName(new ArrayList<>(germplasmNames), brAPIProgram);
            existingGermplasms.forEach(existingGermplasm ->
                    existingGermplasmMap.put(existingGermplasm.getGermplasmName(), existingGermplasm));

            List<BrAPIObservationUnit> existingOUs = brAPIQueryService.getObservationUnitsByNameAndStudyName(new ArrayList<>(ouNameByStudy), brAPIProgram);
            existingOUs.forEach(existingOU -> existingOUMap.put(existingOU.getObservationUnitName(), existingOU));
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        // Create new germplasm
        List<BrAPIGermplasm> newGermplasmList = new ArrayList<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);

            if (pedigreeImport.getGermplasm() != null) {

                //TODO: Check for duplicate germplasm within the file?
                Germplasm germplasm = pedigreeImport.getGermplasm();
                if (!existingGermplasmMap.containsKey(germplasm.getGermplasmName())) {
                    BrAPIGermplasm newGermplasm = germplasm.constructBrAPIGermplasm(brAPIProgram.getCommonCropName());
                    newGermplasmList.add(newGermplasm);
                }
            }
        }

        List<BrAPIGermplasm> createdGermplasm = new ArrayList<>();
        if (newGermplasmList.size() > 0) {
            try {
                createdGermplasm = brAPIQueryService.createBrAPIGermplasm(newGermplasmList);
            } catch (ApiException e) {
                throw new InternalServerException(e.toString(), e);
            }
        }
        Map<String, BrAPIGermplasm> allGermplasm = new HashMap<>(existingGermplasmMap);
        createdGermplasm.forEach(germplasm -> allGermplasm.put(germplasm.getGermplasmName(), germplasm));

        // Create new OUs
        List<BrAPIObservationUnit> newObservationUnitList = new ArrayList<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);
            ObservationUnit observationUnit = pedigreeImport.getObservationUnit();
            BrAPIGermplasm germplasm = allGermplasm.get(pedigreeImport.getGermplasm().getGermplasmName());
            if (!existingOUMap.containsKey(observationUnit.getObservationUnitName())) {

                BrAPIObservationUnit newObservationUnit = observationUnit.constructBrAPIObservationUnit(germplasm.getGermplasmDbId());
                newObservationUnit.setProgramDbId(brAPIProgram.getProgramDbId());
                newObservationUnitList.add(newObservationUnit);
            }
        }

        List<BrAPIObservationUnit> createdObservationUnits = new ArrayList<>();
        if (newObservationUnitList.size() > 0) {
            try{
                createdObservationUnits = brAPIQueryService.createBrAPIObservationUnits(newObservationUnitList);
            } catch (ApiException e) {
                throw new InternalServerException(e.toString(), e);
            }
        }
        Map<String, BrAPIObservationUnit> allOUs = new HashMap<>(existingOUMap);
        createdObservationUnits.forEach(observationUnit -> allOUs.put(observationUnit.getObservationUnitName(), observationUnit));

        // Create new crosses
        // TODO: Need to add ability to add crosses to existing germplasm without crosses
        List<BrAPICross> newCrosses = new ArrayList<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);
            Cross cross = pedigreeImport.getCross();
            Germplasm germplasm = pedigreeImport.getGermplasm();
            if (cross != null && germplasm != null) {
                // If germplasm is not created in this round, skip it
                // TODO: Make a better way to check if new germplasm was created
                if (!existingGermplasmMap.containsKey(germplasm.getGermplasmName())) {
                    BrAPICross newCross = cross.getBrAPICross();
                    
                    // TODO: Move the getting of the relationships up so we can check matches before we POST stuff
                    // TODO: Move this into a common findRelationship method
                    ImportRelation motherRelation = cross.getFemaleParent();
                    if (motherRelation.getType() == ImportRelationType.FILE_LOOKUP) {
                        String targetColumn = motherRelation.getTarget();
                        // Construct a map of the target row values
                        Map<String, Integer> targetRowMap = new HashMap<>();
                        for (int k = 0; k < data.rowCount(); k++) {
                            Row targetRow = data.row(k);
                            String targetValue = targetRow.getString(targetColumn);
                            targetRowMap.put(targetValue, k);
                        }
                        // Look for a match
                        Row referenceRow = data.row(i);
                        String referenceColumn = motherRelation.getReference();
                        String referenceValue = referenceRow.getString(referenceColumn);
                        if (targetRowMap.containsKey(referenceValue)) {
                            // Get the matched row
                            PedigreeImport matchedImportRow = pedigreeImports.get(targetRowMap.get(referenceValue));
                            // Find the observation unit
                            BrAPIObservationUnit matchedOU = allOUs.get(matchedImportRow.getObservationUnit().getObservationUnitName());
                            BrAPICrossParent newMother = new BrAPICrossParent();
                            newMother.setGermplasmDbId(matchedOU.getGermplasmDbId());
                            newMother.setGermplasmName(matchedOU.getGermplasmName());
                            newMother.setObservationUnitDbId(matchedOU.getObservationUnitDbId());
                            newMother.setObservationUnitName(matchedOU.getObservationUnitName());
                            //TODO: Other options maybe?
                            newMother.setParentType(BrAPIParentType.FEMALE);
                            newCross.setParent1(newMother);
                        }

                        //TODO: Throw an error if we can't find the reference
                        /*else {
                            throw new UnprocessableEntityException("Target row for female parent not found.");
                        }*/

                    } else if (motherRelation.getType() == ImportRelationType.DB_LOOKUP) {
                        // TODO: Search in the database
                    }

                    ImportRelation fatherRelation = cross.getMaleParent();
                    if (fatherRelation.getType() == ImportRelationType.FILE_LOOKUP) {
                        String targetColumn = fatherRelation.getTarget();
                        // Construct a map of the target row values
                        Map<String, Integer> targetRowMap = new HashMap<>();
                        for (int k = 0; k < data.rowCount(); k++) {
                            Row targetRow = data.row(k);
                            String targetValue = targetRow.getString(targetColumn);
                            targetRowMap.put(targetValue, k);
                        }
                        // Look for a match
                        String referenceColumn = fatherRelation.getReference();
                        Row referenceRow = data.row(i);
                        String referenceValue = referenceRow.getString(referenceColumn);
                        if (targetRowMap.containsKey(referenceValue)) {
                            // Get the matched row
                            PedigreeImport matchedImportRow = pedigreeImports.get(targetRowMap.get(referenceValue));
                            // Find the observation unit
                            BrAPIObservationUnit matchedOU = allOUs.get(matchedImportRow.getObservationUnit().getObservationUnitName());
                            BrAPICrossParent newFather = new BrAPICrossParent();
                            newFather.setGermplasmDbId(matchedOU.getGermplasmDbId());
                            newFather.setGermplasmName(matchedOU.getGermplasmName());
                            newFather.setObservationUnitDbId(matchedOU.getObservationUnitDbId());
                            newFather.setObservationUnitName(matchedOU.getObservationUnitName());
                            //TODO: Other options maybe?
                            newFather.setParentType(BrAPIParentType.MALE);
                            newCross.setParent2(newFather);
                        }
                        //TODO: Throw an error if we can't find the reference
                        /*else {
                            throw new UnprocessableEntityException("Target row for male parent not found.");
                        }*/

                    } else if (fatherRelation.getType() == ImportRelationType.DB_LOOKUP) {
                        // TODO: Search in the database
                    }

                    newCrosses.add(newCross);
                }
            }
        }

        CrossesApi crossesApi = brAPIProvider.getCrossesApi(BrAPIClientType.CORE);
        try {
            crossesApi.crossesPost(newCrosses);
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        //DONE!
    }
}
