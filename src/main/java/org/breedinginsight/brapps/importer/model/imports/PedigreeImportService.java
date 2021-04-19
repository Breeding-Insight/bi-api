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

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.CrossesApi;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.germ.*;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapps.importer.daos.BrAPICrossDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIProgramDAO;
import org.breedinginsight.brapps.importer.model.base.*;
import org.breedinginsight.brapps.importer.model.config.MappedImportRelation;
import org.breedinginsight.brapps.importer.model.config.ImportRelationType;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.services.FileMappingUtil;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class PedigreeImportService extends BrAPIImportService {

    private String IMPORT_TYPE_ID = "PedigreeImport";

    private BrAPIGermplasmDAO brAPIGermplasmDAO;
    private BrAPIProgramDAO brAPIProgramDAO;
    private BrAPICrossDAO brAPICrossDAO;
    private FileMappingUtil fileMappingUtil;

    @Inject
    public PedigreeImportService(FileMappingUtil fileMappingUtil,
                                 BrAPIProgramDAO brAPIProgramDAO, BrAPIGermplasmDAO brAPIGermplasmDAO, BrAPICrossDAO brAPICrossDAO)
    {
        this.fileMappingUtil = fileMappingUtil;
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.brAPIProgramDAO = brAPIProgramDAO;
        this.brAPICrossDAO = brAPICrossDAO;
    }

    @Override
    public PedigreeImport getImportClass() {
        return new PedigreeImport();
    }

    @Override
    public String getImportTypeId() {
        return IMPORT_TYPE_ID;
    }

    @Override
    public ImportPreviewResponse process(List<BrAPIImport> brAPIImports, Table data, Program program, Boolean commit) throws UnprocessableEntityException {

        // TODO: Check if big file works for POSTing
        // TODO: Check that POST brapi to breedbase works

        // TODO: Breedbase work
        // TODO: POST germplasm
        // TODO: POST observation units
        // TODO: POST studies

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
            Optional<BrAPIProgram> optionalBrAPIProgram = brAPIProgramDAO.getProgram(program.getId());
            if (optionalBrAPIProgram.isEmpty()) throw new ApiException("Program was not found in the brapi service");
            brAPIProgram = optionalBrAPIProgram.get();
        } catch (ApiException e) {
            // Our program should be set up already
            throw new InternalServerException(e.toString(), e);
        }

        // Get all of our objects specified in the data file by their unique attributes
        Set<String> germplasmNames = new HashSet<>();
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);
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
                } else {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            String.format("Target field, %s,  not supported", importCross.getFemaleParent().getTargetColumn()));
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
                } else {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            String.format("Target field, %s,  not supported", importCross.getFemaleParent().getTargetColumn()));
                }
            }
        }

        // Setting up our data elements
        List<PedigreeImportPending> mappedBrAPIImport = pedigreeImports.stream()
                .map(pedigreeImport -> new PedigreeImportPending()).collect(Collectors.toList());
        Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByName = new HashMap<>();
        Map<String, PendingImportObject<BrAPICross>> crossByGermplasmName = new HashMap<>();

        // Get existing objects
        try {
            List<BrAPIGermplasm> existingGermplasms = brAPIGermplasmDAO.getGermplasmByName(new ArrayList<>(germplasmNames), brAPIProgram);
            existingGermplasms.forEach(existingGermplasm -> {
                germplasmByName.put(existingGermplasm.getGermplasmName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        // Create new objects
        for (int i = 0; i < pedigreeImports.size(); i++) {
            PedigreeImport pedigreeImport = pedigreeImports.get(i);
            PedigreeImportPending mappedImportRow = mappedBrAPIImport.get(i);
            Germplasm germplasm = pedigreeImport.getGermplasm();
            Cross cross = pedigreeImport.getCross();

            // Germplasm
            if (germplasm != null && germplasm.getGermplasmName() != null) {
                if (!germplasmByName.containsKey(germplasm.getGermplasmName())) {
                    BrAPIGermplasm newGermplasm = germplasm.constructBrAPIGermplasm(brAPIProgram.getCommonCropName());
                    germplasmByName.put(newGermplasm.getGermplasmName(), new PendingImportObject<>(ImportObjectState.NEW, newGermplasm));
                }
                mappedImportRow.setGermplasm(germplasmByName.get(germplasm.getGermplasmName()));
            }

            // Crosses
            // TODO: Fix once search crosses is added to brapi. Right now only creates crosses for new germplasm
            // Crosses produce germplasm. If no germplasm for this row, skip it
            if (cross != null && mappedImportRow.getGermplasm() != null) {
                PendingImportObject<BrAPIGermplasm> germplasmPreview = germplasmByName.get(germplasm.getGermplasmName());

                BrAPICross newCross = cross.getBrAPICross();
                if (!crossByGermplasmName.containsKey(germplasm.getGermplasmName()) &&
                        germplasmPreview.getState() == ImportObjectState.NEW
                ) {
                    crossByGermplasmName.put(germplasm.getGermplasmName(), new PendingImportObject<>(ImportObjectState.NEW, newCross));
                } else if (!crossByGermplasmName.containsKey(germplasm.getGermplasmName())) {
                    //TODO: Need to search crosses. This is just a placeholder for now
                    crossByGermplasmName.put(germplasm.getGermplasmName(), new PendingImportObject<>(ImportObjectState.EXISTING, newCross));
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
                    }
                    newCross.setParent2(father);
                }

                mappedImportRow.setCross(crossByGermplasmName.get(germplasm.getGermplasmName()));
            }
        }


        // Get our new objects to create
        List<BrAPIGermplasm> newGermplasmList = germplasmByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());
        List<BrAPICross> newCrosses = crossByGermplasmName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());


        // Construct our response object
        ImportPreviewResponse response = new ImportPreviewResponse();
        ImportPreviewStatistics germplasmStats = new ImportPreviewStatistics();
        germplasmStats.setNewObjectCount(newGermplasmList.size());
        ImportPreviewStatistics ouStats = new ImportPreviewStatistics();
        ImportPreviewStatistics crossStats = new ImportPreviewStatistics();
        crossStats.setNewObjectCount(newCrosses.size());
        response.setStatistics(Map.of(
                "Germplasm", germplasmStats,
                "Crosses", crossStats
        ));
        response.setRows((List<PendingImport>)(List<?>) mappedBrAPIImport);
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
                    createdGermplasm = brAPIGermplasmDAO.createBrAPIGermplasm(newGermplasmList);
                } catch (ApiException e) {
                    throw new InternalServerException(e.toString(), e);
                }
            }
            // Update our records
            createdGermplasm.forEach(germplasm -> {
                PendingImportObject<BrAPIGermplasm> preview = germplasmByName.get(germplasm.getGermplasmName());
                preview.setBrAPIObject(germplasm);
            });

            // POST Crosses
            // Update the germplasm and observation unit ids in the new crosses
            for (int k = 0; k < mappedBrAPIImport.size(); k++) {
                PedigreeImportPending mappedImportRow = mappedBrAPIImport.get(k);
                if (mappedImportRow.getCross() != null){
                    PendingImportObject<BrAPICross> crossPreview = mappedImportRow.getCross();
                    if (crossPreview.getState() == ImportObjectState.NEW) {
                        BrAPICross cross = crossPreview.getBrAPIObject();
                        if (cross.getParent1() != null){
                            BrAPIGermplasm targetGermplasmParent = germplasmByName.get(cross.getParent1().getGermplasmName()).getBrAPIObject();
                            cross.getParent1().setGermplasmDbId(targetGermplasmParent.getGermplasmDbId());
                        }

                        if (cross.getParent2() != null) {
                            BrAPIGermplasm targetGermplasmParent = germplasmByName.get(cross.getParent2().getGermplasmName()).getBrAPIObject();
                            cross.getParent2().setGermplasmDbId(targetGermplasmParent.getGermplasmDbId());
                        }
                    }

                }
            }

            try {
                brAPICrossDAO.createBrAPICrosses(newCrosses);
            } catch (ApiException e) {
                throw new InternalServerException(e.toString(), e);
            }
        }

        //DONE!
        return response;
    }

    // TODO: Can we make this sort of function generic so all relationships can use it?
    // TODO: OLD
    public List<BrAPICrossParent> getCrossParents(List<MappedImportRelation> relations, Table data, List<PedigreeImportPending> mappedBrAPIImport, BrAPIParentType parentType,
                                                  List<BrAPIGermplasm> existingGermplasm, List<BrAPIObservationUnit> existingOUs) throws UnprocessableEntityException {

        // TODO: Reduce the loops in this
        List<BrAPICrossParent> crossParents = new ArrayList<>();
        MappedImportRelation relation = relations.get(0);
        if (relation != null) {
            List<Pair<PendingImportObject<BrAPIGermplasm>, PendingImportObject<BrAPIObservationUnit>>> targets = new ArrayList<>();
            if (relation.getType() == ImportRelationType.FILE_LOOKUP) {
                List<Pair<Integer, String>> targetRowMatch = fileMappingUtil.findFileRelationships(data, relations);
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
                        PedigreeImportPending matchedImportRow = mappedBrAPIImport.get(targetRowIndex);
                        PendingImportObject<BrAPIObservationUnit> observationUnit = matchedImportRow.getObservationUnit();
                        PendingImportObject<BrAPIGermplasm> germplasm = matchedImportRow.getGermplasm();
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
                    PendingImportObject<BrAPIGermplasm> germplasm = targets.get(k).getLeft();
                    if (germplasm != null) {
                        newParent.setGermplasmName(germplasm.getBrAPIObject().getGermplasmName());
                    }

                    PendingImportObject<BrAPIObservationUnit> observationUnit = targets.get(k).getRight();
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
