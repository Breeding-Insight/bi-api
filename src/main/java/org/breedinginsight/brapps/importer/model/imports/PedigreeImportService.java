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

import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.CrossesApi;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.germ.*;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.model.base.*;
import org.breedinginsight.brapps.importer.model.config.ImportRelation;
import org.breedinginsight.brapps.importer.model.config.ImportRelationType;
import org.breedinginsight.brapps.importer.services.BrAPIFileService;
import org.breedinginsight.brapps.importer.services.BrAPIQueryService;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
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
    public BrAPIPreviewResponse process(List<BrAPIImport> brAPIImports, Table data, Program program, Boolean commit) throws UnprocessableEntityException {

        // TODO: Need to get the whole file to do a legit upload -> Need to wait until POST/GET pattern for file is made (Next card)
        // TODO: Check that POST brapi to breedbase works
        // TODO: Try to change the relationships to just be a lookup based on name. Then can be in file and in db. Use the DB_LOOKUP option for this

        // TODO: Later items
        // TODO: Identify areas in the process for clean up
        // TODO: Tests
        // TODO: Documentation
        // TODO: Need to have a discussion on whether we only create new things, or if we update too. Example: Germplasm attributes, crosses
        // TODO: How do we handle duplicates? Some can be ignore. What if germplasm have the same name, but different attributes? Throw error for now.
        // TODO: Add enum type for value (dropdown on UI) -> New card
        // TODO: Make an @ImportIgnore annotation to be able to ignore certain fields
        // TODO: See if we can reduce the number of loops
        // TODO: Make temp ids for NEW objects so we can still reference them in relationships
        // TODO: Allow for db lookup and file lookup at the same time

        //BrAPI Objects per row
        List<PedigreeImport> pedigreeImports = (List<PedigreeImport>)(List<?>) brAPIImports;

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

        // Add our values for the db lookup to our list
        // Get the first row with a cross. Not ideal, but it works
        Cross importCross = null;
        Iterator<PedigreeImport> iterator = pedigreeImports.iterator();
        while (importCross == null && iterator.hasNext()) {
            PedigreeImport pedigreeImport = iterator.next();
            importCross = pedigreeImport.getCross();
        }
        if (importCross != null) {
            // Check the lookup for the female parent
            if (importCross.getFemaleParent() != null && importCross.getFemaleParent().getType() == ImportRelationType.DB_LOOKUP) {

                List<String> names = pedigreeImports.stream()
                        .filter(pedigreeImport -> pedigreeImport.getCross() != null && pedigreeImport.getCross().getFemaleParent() != null)
                        .map(pedigreeImport -> pedigreeImport.getCross().getFemaleParent().getReferenceValue())
                        .collect(Collectors.toList());

                // TODO: String reference isn't ideal. See if we can get an enum somewhere. Don't want files everywhere though.
                if (importCross.getFemaleParent().getTargetColumn() == "germplasmNames") {
                    germplasmNames.addAll(names);
                } else if (importCross.getFemaleParent().getTargetColumn() == "observationUnitName") {
                    ouNameByStudy.addAll(
                            names.stream().map(name -> new MutablePair<String, String>(name, null)).collect(Collectors.toList())
                    );
                }
            }
            // Check the lookup for the male parent
            if (importCross.getMaleParent() != null && importCross.getMaleParent().getType() == ImportRelationType.DB_LOOKUP) {

                List<String> names = pedigreeImports.stream()
                        .filter(pedigreeImport -> pedigreeImport.getCross() != null && pedigreeImport.getCross().getMaleParent() != null)
                        .map(pedigreeImport -> pedigreeImport.getCross().getMaleParent().getReferenceValue())
                        .collect(Collectors.toList());

                // TODO: String reference isn't ideal. See if we can get an enum somewhere. Don't want files everywhere though.
                if (importCross.getMaleParent().getTargetColumn() == "germplasmNames") {
                    germplasmNames.addAll(names);
                } else if (importCross.getMaleParent().getTargetColumn() == "observationUnitName") {
                    ouNameByStudy.addAll(
                            names.stream().map(name -> new MutablePair<String, String>(name, null)).collect(Collectors.toList())
                    );
                }
            }
        }

        // Setting up our data elements
        List<PedigreeImportBrAPI> mappedBrAPIImport = pedigreeImports.stream()
                .map(pedigreeImport -> new PedigreeImportBrAPI()).collect(Collectors.toList());
        Map<String, BrAPIPreview<BrAPIGermplasm>> germplasmByName = new HashMap<>();
        Map<String, BrAPIPreview<BrAPIObservationUnit>> ouByName = new HashMap<>();
        Map<String, BrAPIPreview<BrAPICross>> crossByGermplasmName = new HashMap<>();

        // Get existing objects
        try {
            List<BrAPIGermplasm> existingGermplasms = brAPIQueryService.getGermplasmByName(new ArrayList<>(germplasmNames), brAPIProgram);
            existingGermplasms.forEach(existingGermplasm -> {
                germplasmByName.put(existingGermplasm.getGermplasmName(), new BrAPIPreview<>(PreviewState.EXISTING, existingGermplasm));
            });

            List<BrAPIObservationUnit> existingOUs = brAPIQueryService.getObservationUnitsByNameAndStudyName(new ArrayList<>(ouNameByStudy), brAPIProgram);
            existingOUs.forEach(existingOU -> {
                ouByName.put(existingOU.getObservationUnitName(), new BrAPIPreview<>(PreviewState.EXISTING, existingOU));
            });
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
            if (germplasm != null && germplasm.getGermplasmName() != null) {
                if (!germplasmByName.containsKey(germplasm.getGermplasmName())) {
                    BrAPIGermplasm newGermplasm = germplasm.constructBrAPIGermplasm(brAPIProgram.getCommonCropName());
                    germplasmByName.put(newGermplasm.getGermplasmName(), new BrAPIPreview<>(PreviewState.NEW, newGermplasm));
                }
                mappedImportRow.setGermplasm(germplasmByName.get(germplasm.getGermplasmName()));
            }

            // Observation units
            if (observationUnit != null && observationUnit.getObservationUnitName() != null) {
                if (!ouByName.containsKey(observationUnit.getObservationUnitName())) {
                    BrAPIObservationUnit newObservationUnit = observationUnit.constructBrAPIObservationUnit();
                    newObservationUnit.setProgramDbId(brAPIProgram.getProgramDbId());
                    if (mappedImportRow.getGermplasm() != null){
                        newObservationUnit.setGermplasmName(mappedImportRow.getGermplasm().getBrAPIObject().getGermplasmName());
                    }
                    ouByName.put(newObservationUnit.getObservationUnitName(), new BrAPIPreview<>(PreviewState.NEW, newObservationUnit));
                }
                mappedImportRow.setObservationUnit(ouByName.get(observationUnit.getObservationUnitName()));
            }

            // Crosses
            // TODO: Fix once search crosses is added to brapi. Right now only creates crosses for new germplasm
            // Crosses produce germplasm. If no germplasm for this row, skip it
            if (cross != null && mappedImportRow.getGermplasm() != null) {
                BrAPIPreview<BrAPIGermplasm> germplasmPreview = germplasmByName.get(germplasm.getGermplasmName());

                BrAPICross newCross = cross.getBrAPICross();
                if (!crossByGermplasmName.containsKey(germplasm.getGermplasmName()) &&
                        germplasmPreview.getState() == PreviewState.NEW
                ) {
                    crossByGermplasmName.put(germplasm.getGermplasmName(), new BrAPIPreview<>(PreviewState.NEW, newCross));
                } else if (!crossByGermplasmName.containsKey(germplasm.getGermplasmName())) {
                    //TODO: Need to search crosses. This is just a placeholder for now
                    crossByGermplasmName.put(germplasm.getGermplasmName(), new BrAPIPreview<>(PreviewState.EXISTING, newCross));
                }

                // Add the mother
                if (cross.getFemaleParent() != null) {
                    BrAPICrossParent mother = new BrAPICrossParent();
                    // TODO: Get a better way to check the column. I don't like this free string
                    if (cross.getFemaleParent().getTargetColumn() == "germplasmName") {
                        if (germplasmByName.containsKey(cross.getFemaleParent().getReferenceValue())) {
                            mother.setGermplasmName(cross.getFemaleParent().getReferenceValue());
                        } else {
                            //TODO: Throw error if the user wants an error
                        }
                    } else if (cross.getFemaleParent().getTargetColumn() == "observationUnitName") {
                        if (ouByName.containsKey(cross.getFemaleParent().getReferenceValue())) {
                            BrAPIObservationUnit crossTargetOU = ouByName.get(cross.getFemaleParent().getReferenceValue()).getBrAPIObject();
                            mother.setObservationUnitName(crossTargetOU.getObservationUnitName());
                            mother.setGermplasmName(crossTargetOU.getGermplasmName());
                        } else {
                            //TODO: Throw error if the user wants an error
                        }
                    }
                    newCross.setParent1(mother);
                }

                // Add father
                if (cross.getMaleParent() != null) {
                    BrAPICrossParent father = new BrAPICrossParent();
                    // TODO: Get a better way to check the column. I don't like this free string
                    if (cross.getFemaleParent().getTargetColumn().equals("germplasmName")) {
                        if (germplasmByName.containsKey(cross.getMaleParent().getReferenceValue())) {
                            father.setGermplasmName(cross.getMaleParent().getReferenceValue());
                        } else {
                            //TODO: Throw error if the user wants an error
                        }
                    } else if (cross.getFemaleParent().getTargetColumn().equals("observationUnitName")) {
                        if (ouByName.containsKey(cross.getFemaleParent().getReferenceValue())) {
                            BrAPIObservationUnit crossTargetOU = ouByName.get(cross.getMaleParent().getReferenceValue()).getBrAPIObject();
                            father.setObservationUnitName(crossTargetOU.getObservationUnitName());
                            father.setGermplasmName(crossTargetOU.getGermplasmName());
                        } else {
                            //TODO: Throw error if the user wants an error
                        }
                    }
                    newCross.setParent2(father);
                }

                mappedImportRow.setCross(crossByGermplasmName.get(germplasm.getGermplasmName()));
            }
        }

        // Get the relationships
        /*if (pedigreeImports.get(0).getCross() != null) {
            List<ImportRelation> motherRelations = pedigreeImports.stream().map(pedigreeImport -> {
                return pedigreeImport.getCross() != null ? pedigreeImport.getCross().getFemaleParent() : null;
            }).collect(Collectors.toList());
            // unique
            List<BrAPICrossParent> mothers = getCrossParents(motherRelations, data, mappedBrAPIImport, BrAPIParentType.FEMALE);
            for (int k = 0; k < mothers.size(); k++) {
                //TODO: Does this reference to the preview in the map?
                if (mappedBrAPIImport.get(k).getCross() != null) {
                    BrAPICross cross = mappedBrAPIImport.get(k).getCross().getBrAPIObject();
                    if (cross.getParent1() == null){
                        mappedBrAPIImport.get(k).getCross().getBrAPIObject().setParent1(mothers.get(k));
                    }
                }
            }

            List<ImportRelation> fatherRelations = pedigreeImports.stream().map(pedigreeImport -> {
                return pedigreeImport.getCross() != null ? pedigreeImport.getCross().getMaleParent() : null;
            }).collect(Collectors.toList());
            List<BrAPICrossParent> fathers = getCrossParents(fatherRelations, data, mappedBrAPIImport, BrAPIParentType.MALE);
            for (int k = 0; k < fathers.size(); k++) {
                if (mappedBrAPIImport.get(k).getCross() != null) {
                    BrAPICross cross = mappedBrAPIImport.get(k).getCross().getBrAPIObject();
                    if (cross.getParent2() == null){
                        mappedBrAPIImport.get(k).getCross().getBrAPIObject().setParent2(fathers.get(k));
                    }
                }
            }
        }*/

        // Get our new objects to create
        List<BrAPIGermplasm> newGermplasmList = germplasmByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == PreviewState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());
        List<BrAPIObservationUnit> newOUList = ouByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == PreviewState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());
        List<BrAPICross> newCrosses = crossByGermplasmName.values().stream()
                .filter(preview -> preview != null && preview.getState() == PreviewState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());


        // Construct our response object
        BrAPIPreviewResponse response = new BrAPIPreviewResponse();
        BrAPIPreviewStatistics germplasmStats = new BrAPIPreviewStatistics();
        germplasmStats.setNewObjectCount(newGermplasmList.size());
        BrAPIPreviewStatistics ouStats = new BrAPIPreviewStatistics();
        ouStats.setNewObjectCount(newOUList.size());
        BrAPIPreviewStatistics crossStats = new BrAPIPreviewStatistics();
        crossStats.setNewObjectCount(newCrosses.size());
        response.setStatistics(Map.of(
                "Germplasm", germplasmStats,
                "Observation Units", ouStats,
                "Crosses", crossStats
        ));
        response.setRows((List<MappedImport>)(List<?>) mappedBrAPIImport);
        // Preview Class
        //  statistics
        //    Germplasm
        //      new
        //    ObservationUnits
        //      new
        //  rows
        //    germplasm BrAPIPreview
        //    ou  BrAPIPReview
        if (!commit) {
            return response;
        } else {

            // POST Germplasm
            List<BrAPIGermplasm> createdGermplasm = new ArrayList<>();
            if (newGermplasmList.size() > 0) {
                try {
                    createdGermplasm = brAPIQueryService.createBrAPIGermplasm(newGermplasmList);
                } catch (ApiException e) {
                    throw new InternalServerException(e.toString(), e);
                }
            }
            // Update our records
            createdGermplasm.forEach(germplasm -> {
                BrAPIPreview<BrAPIGermplasm> preview = germplasmByName.get(germplasm.getGermplasmName());
                preview.setBrAPIObject(germplasm);
            });

            // POST Observation Units
            // Update the germplasm id from our new germplasm objects
            for (int k = 0; k < mappedBrAPIImport.size(); k++){
                PedigreeImportBrAPI mappedImportRow = mappedBrAPIImport.get(k);
                if (mappedImportRow.getObservationUnit() != null && mappedImportRow.getGermplasm() != null &&
                    mappedImportRow.getObservationUnit().getState() == PreviewState.NEW
                ) {
                    BrAPIGermplasm ouGermplasm = mappedImportRow.getGermplasm().getBrAPIObject();
                    mappedImportRow.getObservationUnit().getBrAPIObject().setGermplasmDbId(ouGermplasm.getGermplasmDbId());
                }
            }

            List<BrAPIObservationUnit> createdObservationUnits = new ArrayList<>();
            if (newOUList.size() > 0) {
                try{
                    createdObservationUnits = brAPIQueryService.createBrAPIObservationUnits(newOUList);
                } catch (ApiException e) {
                    throw new InternalServerException(e.toString(), e);
                }
            }
            createdObservationUnits.forEach(observationUnit -> {
                BrAPIPreview<BrAPIObservationUnit> preview = ouByName.get(observationUnit.getObservationUnitName());
                preview.setBrAPIObject(observationUnit);
            });

            // POST Crosses
            // Update the germplasm and observation unit ids in the new crosses
            for (int k = 0; k < mappedBrAPIImport.size(); k++) {
                PedigreeImportBrAPI mappedImportRow = mappedBrAPIImport.get(k);
                if (mappedImportRow.getCross() != null){
                    BrAPIPreview<BrAPICross> crossPreview = mappedImportRow.getCross();
                    if (crossPreview.getState() == PreviewState.NEW) {
                        BrAPICross cross = crossPreview.getBrAPIObject();
                        if (cross.getParent1() != null){
                            BrAPIGermplasm targetGermplasmParent = germplasmByName.get(cross.getParent1().getGermplasmName()).getBrAPIObject();
                            BrAPIObservationUnit targetOUParent = ouByName.get(cross.getParent1().getObservationUnitName()).getBrAPIObject();
                            cross.getParent1().setGermplasmDbId(targetGermplasmParent.getGermplasmDbId());
                            cross.getParent1().setObservationUnitDbId(targetOUParent.getObservationUnitDbId());
                        }

                        if (cross.getParent2() != null) {
                            BrAPIGermplasm targetGermplasmParent = germplasmByName.get(cross.getParent2().getGermplasmName()).getBrAPIObject();
                            BrAPIObservationUnit targetOUParent = ouByName.get(cross.getParent2().getObservationUnitName()).getBrAPIObject();
                            cross.getParent2().setGermplasmDbId(targetGermplasmParent.getGermplasmDbId());
                            cross.getParent2().setObservationUnitDbId(targetOUParent.getObservationUnitDbId());
                        }
                    }

                }
            }

            CrossesApi crossesApi = brAPIProvider.getCrossesApi(BrAPIClientType.CORE);
            try {
                crossesApi.crossesPost(newCrosses);
            } catch (ApiException e) {
                throw new InternalServerException(e.toString(), e);
            }
        }

        //DONE!
        return response;
    }

    // TODO: Can we make this sort of function generic so all relationships can use it?
    // TODO: OLD
    public List<BrAPICrossParent> getCrossParents(List<ImportRelation> relations, Table data, List<PedigreeImportBrAPI> mappedBrAPIImport, BrAPIParentType parentType,
                                                  List<BrAPIGermplasm> existingGermplasm, List<BrAPIObservationUnit> existingOUs) throws UnprocessableEntityException {

        // TODO: Reduce the loops in this
        List<BrAPICrossParent> crossParents = new ArrayList<>();
        ImportRelation relation = relations.get(0);
        if (relation != null) {
            List<Pair<BrAPIPreview<BrAPIGermplasm>, BrAPIPreview<BrAPIObservationUnit>>> targets = new ArrayList<>();
            if (relation.getType() == ImportRelationType.FILE_LOOKUP) {
                List<Pair<Integer, String>> targetRowMatch = brAPIFileService.findFileRelationships(data, relations);
                // Generate targets array
                for (int k = 0; k < targetRowMatch.size(); k++) {
                    Integer targetRowIndex = targetRowMatch.get(k).getLeft();
                    String rowReferenceValue = targetRowMatch.get(k).getRight();
                    if (StringUtils.isBlank(rowReferenceValue)){
                        targets.add(null);
                    } else if (targetRowIndex == -1) {
                        // TODO: Throw error here if no match was found. Wait until we can show errors on the preview page as part of review. Or allow user to choose this option.
                        targets.add(null);
                        //throw new UnprocessableEntityException(String.format("Relationship not found for row %s, column %s", k, relation.getReference()));
                    } else {
                        PedigreeImportBrAPI matchedImportRow = mappedBrAPIImport.get(targetRowIndex);
                        BrAPIPreview<BrAPIObservationUnit> observationUnit = matchedImportRow.getObservationUnit();
                        BrAPIPreview<BrAPIGermplasm> germplasm = matchedImportRow.getGermplasm();
                        targets.add(new MutablePair<>(germplasm, observationUnit));
                    }
                }
            } else if (relation.getType() == ImportRelationType.DB_LOOKUP) {
                // TODO: Do the DB lookup
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
