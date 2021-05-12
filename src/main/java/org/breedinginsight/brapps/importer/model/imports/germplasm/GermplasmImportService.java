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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.germ.*;
import org.breedinginsight.brapps.importer.daos.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIProgramDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.base.*;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImportService;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewResponse;
import org.breedinginsight.brapps.importer.model.response.ImportPreviewStatistics;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.jooq.JSONB;
import tech.tablesaw.api.Table;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class GermplasmImportService extends BrAPIImportService {

    private String IMPORT_TYPE_ID = "GermplasmImport";

    private BrAPIGermplasmDAO brAPIGermplasmDAO;
    private BrAPIProgramDAO brAPIProgramDAO;
    private ImportDAO importDAO;
    private ObjectMapper objMapper;

    @Inject
    public GermplasmImportService(BrAPIProgramDAO brAPIProgramDAO, BrAPIGermplasmDAO brAPIGermplasmDAO, ImportDAO importDAO,
                                  ObjectMapper objMapper)
    {
        this.brAPIGermplasmDAO = brAPIGermplasmDAO;
        this.brAPIProgramDAO = brAPIProgramDAO;
        this.importDAO = importDAO;
        this.objMapper = objMapper;
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
    public ImportPreviewResponse process(List<BrAPIImport> brAPIImports, Table data, Program program, ImportUpload upload, Boolean commit)
            throws UnprocessableEntityException, DoesNotExistException {

        //BrAPI Objects per row
        List<GermplasmImport> germplasmImports = (List<GermplasmImport>)(List<?>) brAPIImports;

        // Get BrAPI Program
        upload.getProgress().setMessage("Checking program in brapi service");
        importDAO.update(upload);
        BrAPIProgram brAPIProgram;
        try {
            Optional<BrAPIProgram> optionalBrAPIProgram = brAPIProgramDAO.getProgram(program.getId());
            if (optionalBrAPIProgram.isEmpty()) throw new DoesNotExistException("Program was not found in the brapi service");
            brAPIProgram = optionalBrAPIProgram.get();
        } catch (ApiException e) {
            throw new InternalServerException(e.toString(), e);
        }

        // Get all of our objects specified in the data file by their unique attributes
        upload.getProgress().setMessage("Checking existing germplasm in brapi service");
        importDAO.update(upload);
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
        List<GermplasmImportPending> mappedBrAPIImport = new ArrayList<>();
        Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByName = new HashMap<>();

        // Get existing objects
        List<BrAPIGermplasm> existingGermplasms;
        try {
            existingGermplasms = brAPIGermplasmDAO.getGermplasmByName(new ArrayList<>(germplasmNames), program.getId(), brAPIProgram);
            existingGermplasms.forEach(existingGermplasm -> {
                germplasmByName.put(existingGermplasm.getGermplasmName(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm));
            });
        } catch (ApiException e) {
            // We shouldn't get an error back from our services. If we do, nothing the user can do about it
            throw new InternalServerException(e.toString(), e);
        }

        // Create new objects
        upload.getProgress().setMessage("Creating brapi objects from data");
        importDAO.update(upload);
        for (int i = 0; i < germplasmImports.size(); i++) {
            GermplasmImport germplasmImport = germplasmImports.get(i);
            GermplasmImportPending mappedImportRow = new GermplasmImportPending();
            mappedBrAPIImport.add(mappedImportRow);
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

        // Save our results to the db
        String json = null;
        try {
            json = objMapper.writeValueAsString(response);
        } catch(JsonProcessingException e) {
            log.error("Problem converting mapping to json", e);
            // If we didn't catch this error in the validator, this is an unexpected server error.
            throw new InternalServerException("Problem converting mapping to json", e);
        }
        upload.setMappedData(JSONB.valueOf(json));
        upload.getProgress().setMessage("Finished mapping data to brapi objects");
        importDAO.update(upload);

        if (!commit) {
            // Set our upload in the db to be finished
            upload.getProgress().setStatuscode((short) HttpStatus.OK.getCode());
            importDAO.update(upload);
            return response;
        } else {
            // POST Germplasm
            upload.getProgress().setTotal((long) newGermplasmList.size());
            upload.getProgress().setMessage("Creating new germplasm in brapi service");
            importDAO.update(upload);
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

            // Update our records
            //TODO: Get the progress to update in the db
            createdGermplasm.forEach(germplasm -> {
                PendingImportObject<BrAPIGermplasm> preview = germplasmByName.get(germplasm.getGermplasmName());
                preview.setBrAPIObject(germplasm);
            });

            upload.getProgress().setMessage("Finished!");
            upload.getProgress().setStatuscode((short) HttpStatus.OK.getCode());
            importDAO.update(upload);
        }

        //DONE!
        return response;
    }
}
