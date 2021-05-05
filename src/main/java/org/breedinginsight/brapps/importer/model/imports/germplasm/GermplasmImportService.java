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

package org.breedinginsight.brapps.importer.model.imports.germplasm;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.germ.*;
import org.breedinginsight.brapps.importer.daos.BrAPICrossDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIProgramDAO;
import org.breedinginsight.brapps.importer.model.base.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
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
public class GermplasmImportService extends BrAPIImportService {

    private String IMPORT_TYPE_ID = "GermplasmImport";

    private BrAPIGermplasmDAO brAPIGermplasmDAO;
    private BrAPIProgramDAO brAPIProgramDAO;

    @Inject
    public GermplasmImportService(FileMappingUtil fileMappingUtil,
                                  BrAPIProgramDAO brAPIProgramDAO, BrAPIGermplasmDAO brAPIGermplasmDAO, BrAPICrossDAO brAPICrossDAO)
    {
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.brAPIProgramDAO = brAPIProgramDAO;
    }

    @Override
    public GermplasmImport getImportClass() {
        return new GermplasmImport();
    }

    @Override
    public String getImportTypeId() {
        return IMPORT_TYPE_ID;
    }

    @Override
    public ImportPreviewResponse process(List<BrAPIImport> brAPIImports, Table data, Program program, Boolean commit) throws UnprocessableEntityException {

        //BrAPI Objects per row
        List<GermplasmImport> germplasmImports = (List<GermplasmImport>)(List<?>) brAPIImports;

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
        for (int i = 0; i < germplasmImports.size(); i++) {
            GermplasmImport germplasmImport = germplasmImports.get(i);
            if (germplasmImport.getGermplasm() != null){
                if (germplasmImport.getGermplasm().getGermplasmName() != null) {
                    germplasmNames.add(germplasmImport.getGermplasm().getGermplasmName());
                }

                if (germplasmImport.getGermplasm().getFemaleParent() != null) {
                    germplasmNames.add(germplasmImport.getGermplasm().getFemaleParent().getReferenceValue());
                }

                if (germplasmImport.getGermplasm().getMaleParent() != null) {
                    germplasmNames.add(germplasmImport.getGermplasm().getMaleParent().getReferenceValue());
                }
            }
        }

        // Setting up our data elements
        List<GermplasmImportPending> mappedBrAPIImport = germplasmImports.stream()
                .map(germplasmImport -> new GermplasmImportPending()).collect(Collectors.toList());
        Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByName = new HashMap<>();

        // Get existing objects
        List<BrAPIGermplasm> existingGermplasms;
        try {
            existingGermplasms = brAPIGermplasmDAO.getGermplasmByName(new ArrayList<>(germplasmNames), brAPIProgram);
            existingGermplasms.forEach(existingGermplasm -> {
                germplasmByName.put(existingGermplasm.getGermplasmName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        // Create new objects
        for (int i = 0; i < germplasmImports.size(); i++) {
            GermplasmImport germplasmImport = germplasmImports.get(i);
            GermplasmImportPending mappedImportRow = mappedBrAPIImport.get(i);
            Germplasm germplasm = germplasmImport.getGermplasm();

            // Germplasm
            if (germplasm != null && germplasm.getGermplasmName() != null) {
                if (!germplasmByName.containsKey(germplasm.getGermplasmName())) {
                    BrAPIGermplasm newGermplasm = germplasm.constructBrAPIGermplasm(brAPIProgram);

                    // Check the parents exist
                    String femaleParent = germplasm.getFemaleParent() != null ? germplasm.getFemaleParent().getReferenceValue() : null;
                    String maleParent = germplasm.getMaleParent() != null ? germplasm.getMaleParent().getReferenceValue() : null;
                    Boolean femaleParentFound = true;
                    String pedigreeString = null;
                    if (femaleParent != null){
                        if (germplasmByName.containsKey(femaleParent)){
                            // Good to go
                            pedigreeString = femaleParent;
                        } else {
                            femaleParentFound = false;
                        }
                    }

                    if (maleParent != null && femaleParentFound) {
                        if (germplasmByName.containsKey(maleParent)){
                            // Good to go
                            pedigreeString += "/" + maleParent;
                        }
                    }
                    newGermplasm.setPedigree(pedigreeString);

                    germplasmByName.put(newGermplasm.getGermplasmName(), new PendingImportObject<>(ImportObjectState.NEW, newGermplasm));
                }
                mappedImportRow.setGermplasm(germplasmByName.get(germplasm.getGermplasmName()));
            }
        }


        // Get our new objects to create
        List<BrAPIGermplasm> newGermplasmList = germplasmByName.values().stream()
                .filter(preview -> preview != null && preview.getState() == ImportObjectState.NEW)
                .map(preview -> preview.getBrAPIObject())
                .collect(Collectors.toList());

        // Construct a dependency tree for POSTing order
        Set<String> created = new HashSet<>();
        created.addAll(existingGermplasms.stream().map(brAPIGermplasm -> brAPIGermplasm.getGermplasmName()).collect(Collectors.toList()));
        List<List<BrAPIGermplasm>> postOrder = new ArrayList<>();
        Integer totalRecorded = 0;

        while (totalRecorded < newGermplasmList.size()) {
            List<BrAPIGermplasm> createList = new ArrayList<>();
            for (BrAPIGermplasm germplasm: newGermplasmList ){
                if (created.contains(germplasm.getGermplasmName())) continue;

                // If it has no dependencies, add it
                if (germplasm.getPedigree() == null) {
                    createList.add(germplasm);
                    continue;
                }

                // If both parents have been created already, add it
                List<String> pedigreeArray = List.of(germplasm.getPedigree().split("/"));
                String femaleParent = pedigreeArray.get(0);
                String maleParent = pedigreeArray.size() > 1 ? pedigreeArray.get(1) : null;
                if (created.contains(femaleParent)){
                    if (maleParent == null){
                        createList.add(germplasm);
                    } else if (created.contains(maleParent)) {
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

        // Construct our response object
        ImportPreviewResponse response = new ImportPreviewResponse();
        ImportPreviewStatistics germplasmStats = new ImportPreviewStatistics();
        germplasmStats.setNewObjectCount(newGermplasmList.size());
        ImportPreviewStatistics pedigreeConnectStats = new ImportPreviewStatistics();
        pedigreeConnectStats.setNewObjectCount(
                newGermplasmList.stream().filter(newGermplasm -> newGermplasm != null).collect(Collectors.toList()).size());
        pedigreeConnectStats.setIgnoredObjectCount(
                germplasmImports.stream().filter(germplasmImport ->
                        germplasmImport.getGermplasm() != null &&
                                (germplasmImport.getGermplasm().getFemaleParent() != null || germplasmImport.getGermplasm().getMaleParent() != null)
                ).collect(Collectors.toList()).size() - pedigreeConnectStats.getNewObjectCount());
        response.setStatistics(Map.of(
                "Germplasm", germplasmStats,
                "Pedigree Connections", pedigreeConnectStats
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
                    for (List<BrAPIGermplasm> postGroup: postOrder){
                        createdGermplasm.addAll(brAPIGermplasmDAO.createBrAPIGermplasm(postGroup));
                    }
                } catch (ApiException e) {
                    throw new InternalServerException(e.toString(), e);
                }
            }

            // Update our records
            createdGermplasm.forEach(germplasm -> {
                PendingImportObject<BrAPIGermplasm> preview = germplasmByName.get(germplasm.getGermplasmName());
                preview.setBrAPIObject(germplasm);
            });
        }

        //DONE!
        return response;
    }
}
