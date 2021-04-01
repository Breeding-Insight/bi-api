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
import org.apache.commons.lang3.StringUtils;
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
import org.breedinginsight.brapps.importer.services.BrAPIFileService;
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
    private BrAPIFileService brAPIFileService;
    private BrAPIProvider brAPIProvider;

    @Inject
    public PedigreeImportService(BrAPIQueryService brAPIQueryService, BrAPIFileService brAPIFileService, BrAPIProvider brAPIProvider)
    {
        this.brAPIQueryService = brAPIQueryService;
        this.brAPIFileService = brAPIFileService;
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
    public List<MappedImport> process(List<BrAPIImport> brAPIImports, Table data, Program program, Boolean commit) throws UnprocessableEntityException {

        // TODO: Check that POST brapi to breedbase works
        // TODO: Test what happens when objects not specified

        // TODO: Later items
        // TODO: Identify areas in the process for clean up
        // TODO: Tests
        // TODO: Documentation
        // TODO: Need to have a discussion on whether we only create new things, or if we update too. Example: Germplasm attributes, crosses
        // TODO: How do we handle duplicates? Some can be ignore. What if germplasm have the same name, but different attributes? Throw error for now.
        // TODO: Add enum type for value (dropdown on UI) -> New card
        // TODO: Need to get the whole file to do a legit upload -> Need to wait until POST/GET pattern for file is made (Next card)
        // TODO: Make an @ImportIgnore annotation to be able to ignore certain fields
        // TODO: See if we can reduce the number of loops
        // TODO: Make temp ids for NEW objects so we can still reference them in relationships

        //BrAPI Objects per row
        List<PedigreeImport> pedigreeImports = (List<PedigreeImport>)(List<?>) brAPIImports;
        List<PedigreeImportBrAPI> mappedBrAPIImport = pedigreeImports.stream()
                .map(pedigreeImport -> new PedigreeImportBrAPI()).collect(Collectors.toList());

        // Get BrAPI Program
        BrAPIProgram brAPIProgram;
        try {
            Optional<BrAPIProgram> optionalBrAPIProgram = brAPIQueryService.getProgram(program.getId());
            if (optionalBrAPIProgram.isEmpty()) throw new ApiException("Program was not found in the brapi service");
            brAPIProgram = optionalBrAPIProgram.get();
        } catch (ApiException e) {
            // Our program should be set up already
            throw new InternalServerException(e.toString(), e);
        }

        // Get all of our objects specified in the data file by their unique attributes
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

        // Get existing objects
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

        // Create new objects
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);
            PedigreeImportBrAPI mappedImportRow = mappedBrAPIImport.get(i);
            Germplasm germplasm = pedigreeImport.getGermplasm();
            ObservationUnit observationUnit = pedigreeImport.getObservationUnit();
            Cross cross = pedigreeImport.getCross();

            // Germplasm
            if (germplasm != null) {
                if (!existingGermplasmMap.containsKey(germplasm.getGermplasmName())) {
                    BrAPIGermplasm newGermplasm = germplasm.constructBrAPIGermplasm(brAPIProgram.getCommonCropName());
                    mappedImportRow.setGermplasm(new BrAPIPreview<>(PreviewState.NEW, newGermplasm));
                } else {
                    mappedImportRow.setGermplasm(new BrAPIPreview<>(PreviewState.EXISTING, existingGermplasmMap.get(germplasm.getGermplasmName())));
                }
            }

            // Observation units
            if (observationUnit != null) {
                if (!existingOUMap.containsKey(observationUnit.getObservationUnitName())) {
                    BrAPIObservationUnit newObservationUnit = observationUnit.constructBrAPIObservationUnit();
                    newObservationUnit.setProgramDbId(brAPIProgram.getProgramDbId());
                    mappedImportRow.setObservationUnit(new BrAPIPreview<>(PreviewState.NEW, newObservationUnit));
                } else {
                    mappedImportRow.setObservationUnit(new BrAPIPreview<>(PreviewState.EXISTING, existingOUMap.get(observationUnit.getObservationUnitName())));
                }
            }

            // Crosses
            if (cross != null && germplasm != null) {
                if (!existingGermplasmMap.containsKey(germplasm.getGermplasmName())) {
                    BrAPICross newCross = cross.getBrAPICross();
                    mappedImportRow.setCross(new BrAPIPreview<>(PreviewState.NEW, newCross));
                } else {
                    //TODO: Need to search crosses. This is just a placeholder for now
                    BrAPICross newCross = cross.getBrAPICross();
                    mappedImportRow.setCross(new BrAPIPreview<>(PreviewState.EXISTING, newCross));
                }
            }
        }

        // Get the relationships
        if (pedigreeImports.get(0).getCross() != null) {
            ImportRelation motherRelation = pedigreeImports.get(0).getCross().getFemaleParent(); // unique
            List<BrAPICrossParent> mothers = getCrossParents(motherRelation, data, mappedBrAPIImport, BrAPIParentType.FEMALE);
            for (int k = 0; k < mothers.size(); k++) {
                mappedBrAPIImport.get(k).getCross().getBrAPIObject().setParent1(mothers.get(k));
            }

            ImportRelation fatherRelation = pedigreeImports.get(0).getCross().getMaleParent();
            List<BrAPICrossParent> fathers = getCrossParents(motherRelation, data, mappedBrAPIImport, BrAPIParentType.MALE);
            for (int k = 0; k < fathers.size(); k++) {
                mappedBrAPIImport.get(k).getCross().getBrAPIObject().setParent2(fathers.get(k));
            }
        }

        if (!commit) {
            return (List<MappedImport>)(List<?>) mappedBrAPIImport;
        } else {

            // POST Germplasm
            //TODO: Make this a generic in the abstract super class
            List<BrAPIGermplasm> newGermplasmList = mappedBrAPIImport.stream()
                    .map(mappedImportRow -> mappedImportRow.getGermplasm())
                    .filter(germplasmPreview -> germplasmPreview != null && germplasmPreview.getState() == PreviewState.NEW)
                    .map(germplasmPreview -> germplasmPreview.getBrAPIObject())
                    .collect(Collectors.toList());
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

            // POST Observation Units
            // Update the germplasm id from our new germplasm objects
            for (int k = 0; k < mappedBrAPIImport.size(); k++){
                PedigreeImportBrAPI mappedImportRow = mappedBrAPIImport.get(k);
                if (mappedImportRow.getObservationUnit() != null && mappedImportRow.getGermplasm() != null) {
                    String ouGermplasmName = mappedImportRow.getGermplasm().getBrAPIObject().getGermplasmName();
                    BrAPIGermplasm ouGermplasm = allGermplasm.get(ouGermplasmName);
                    mappedImportRow.getObservationUnit().getBrAPIObject().setGermplasmDbId(ouGermplasm.getGermplasmDbId());
                }
            }

            List<BrAPIObservationUnit> newOUList = mappedBrAPIImport.stream()
                    .map(mappedImportRow -> mappedImportRow.getObservationUnit())
                    .filter(ouPreview -> ouPreview != null && ouPreview.getState() == PreviewState.NEW)
                    .map(ouPreview -> ouPreview.getBrAPIObject())
                    .collect(Collectors.toList());
            List<BrAPIObservationUnit> createdObservationUnits = new ArrayList<>();
            if (newOUList.size() > 0) {
                try{
                    createdObservationUnits = brAPIQueryService.createBrAPIObservationUnits(newOUList);
                } catch (ApiException e) {
                    throw new InternalServerException(e.toString(), e);
                }
            }
            Map<String, BrAPIObservationUnit> allOUs = new HashMap<>(existingOUMap);
            createdObservationUnits.forEach(observationUnit -> allOUs.put(observationUnit.getObservationUnitName(), observationUnit));

            // POST Crosses
            // Update the germplasm and observation unit ids in the cross
            for (int k = 0; k < mappedBrAPIImport.size(); k++) {
                PedigreeImportBrAPI mappedImportRow = mappedBrAPIImport.get(k);
                if (mappedImportRow.getCross() != null){
                    BrAPICross cross = mappedImportRow.getCross().getBrAPIObject();
                    if (cross.getParent1() != null){
                        BrAPIGermplasm targetGermplasmParent1 = allGermplasm.get(cross.getParent1().getGermplasmName());
                        BrAPIObservationUnit targetOUParent1 = allOUs.get(cross.getParent1().getObservationUnitName());
                        cross.getParent1().setGermplasmDbId(targetGermplasmParent1.getGermplasmDbId());
                        cross.getParent1().setObservationUnitDbId(targetOUParent1.getObservationUnitDbId());
                    }

                    if (cross.getParent2() != null) {
                        BrAPIGermplasm targetGermplasmNameParent2 = allGermplasm.get(cross.getParent2().getGermplasmName());
                        BrAPIObservationUnit targetOUParent2 = allOUs.get(cross.getParent2().getObservationUnitName());
                        cross.getParent2().setGermplasmDbId(targetGermplasmNameParent2.getGermplasmDbId());
                        cross.getParent2().setObservationUnitDbId(targetOUParent2.getObservationUnitDbId());
                    }
                }
            }

            List<BrAPICross> newCrosses = mappedBrAPIImport.stream()
                    .map(mappedImportRow -> mappedImportRow.getCross())
                    .filter(preview -> preview != null && preview.getState() == PreviewState.NEW)
                    .map(preview -> preview.getBrAPIObject())
                    .collect(Collectors.toList());
            CrossesApi crossesApi = brAPIProvider.getCrossesApi(BrAPIClientType.CORE);
            try {
                crossesApi.crossesPost(newCrosses);
            } catch (ApiException e) {
                throw new InternalServerException(e.toString(), e);
            }
        }

        //DONE!
        return (List<MappedImport>)(List<?>) mappedBrAPIImport;
    }

    // TODO: Can we make this sort of function generic so all relationships can use it?
    public List<BrAPICrossParent> getCrossParents(ImportRelation relation, Table data, List<PedigreeImportBrAPI> mappedBrAPIImport, BrAPIParentType parentType) throws UnprocessableEntityException {

        // TODO: Reduce the loops in this
        List<BrAPICrossParent> crossParents = new ArrayList<>();
        if (relation != null) {
            List<Pair<BrAPIPreview<BrAPIGermplasm>, BrAPIPreview<BrAPIObservationUnit>>> targets = new ArrayList<>();
            if (relation.getType() == ImportRelationType.FILE_LOOKUP) {
                List<Pair<Integer, String>> targetRowMatch = brAPIFileService.findFileRelationships(data, relation);
                // Generate targets array
                for (int k = 0; k < targetRowMatch.size(); k++) {
                    // TODO: Throw error here if no match was found
                    Integer targetRowIndex = targetRowMatch.get(k).getLeft();
                    String rowReferenceValue = targetRowMatch.get(k).getRight();
                    if (StringUtils.isBlank(rowReferenceValue)){
                        targets.add(null);
                    } else if (targetRowIndex == -1) {
                        throw new UnprocessableEntityException(String.format("Relationship not found for row %s, column %s", k, relation.getReference()));
                    } else {
                        PedigreeImportBrAPI matchedImportRow = mappedBrAPIImport.get(targetRowIndex);
                        BrAPIPreview<BrAPIObservationUnit> observationUnit = matchedImportRow.getObservationUnit();
                        BrAPIPreview<BrAPIGermplasm> germplasm = matchedImportRow.getGermplasm();
                        targets.add(new MutablePair<>(germplasm, observationUnit));
                    }
                }
            } else if (relation.getType() == ImportRelationType.DB_LOOKUP) {
                // TODO: Do the DB lookup
                // Add the found germplasm to the existing germplasm list
            }

            // Construct the cross parent object
            for (int k = 0; k < targets.size(); k++) {
                if (targets.get(k) != null) {
                    BrAPICrossParent newParent = new BrAPICrossParent();
                    //TODO: Option allowed to be set by user perhaps?
                    newParent.setParentType(parentType);
                    BrAPIPreview<BrAPIGermplasm> germplasm = targets.get(k).getLeft();
                    if (germplasm != null) {
                        newParent.setGermplasmName(germplasm.getBrAPIObject().getGermplasmName());
                    }

                    BrAPIPreview<BrAPIObservationUnit> observationUnit = targets.get(k).getRight();
                    if (observationUnit != null) {
                        // Study is null for these ous, so the name is the unique identifier
                        newParent.setObservationUnitName(observationUnit.getBrAPIObject().getObservationUnitName());
                    }
                    crossParents.add(newParent);
                } else {
                    crossParents.add(null);
                }
            }
        }

        return crossParents;
    }
}
