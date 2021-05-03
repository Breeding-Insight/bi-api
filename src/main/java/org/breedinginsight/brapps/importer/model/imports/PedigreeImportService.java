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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.germ.*;
import org.breedinginsight.brapps.importer.daos.BrAPICrossDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIProgramDAO;
import org.breedinginsight.brapps.importer.model.base.*;
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
            if (pedigreeImport.getGermplasm() != null && pedigreeImport.getGermplasm().getGermplasmName() != null){
                germplasmNames.add(pedigreeImport.getGermplasm().getGermplasmName());
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

                if (importCross.getFemaleParent().getTargetColumn().equals(Cross.GERMPLASM_NAME_TARGET)) {
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

                if (importCross.getMaleParent().getTargetColumn().equals(Cross.GERMPLASM_NAME_TARGET)) {
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
                Boolean addCross = true;

                // Add the mother
                if (cross.getFemaleParent() != null) {
                    BrAPICrossParent mother = new BrAPICrossParent();
                    if (cross.getFemaleParent().getTargetColumn().equals(Cross.GERMPLASM_NAME_TARGET)) {
                        if (germplasmByName.containsKey(cross.getFemaleParent().getReferenceValue())) {
                            mother.setGermplasmName(cross.getFemaleParent().getReferenceValue());
                        } else {
                            //TODO: Throw error if the user wants an error
                            addCross = false;
                        }
                    }
                    newCross.setParent1(mother);
                }

                // Add father
                if (cross.getMaleParent() != null) {
                    BrAPICrossParent father = new BrAPICrossParent();
                    if (cross.getFemaleParent().getTargetColumn().equals(Cross.GERMPLASM_NAME_TARGET)) {
                        if (germplasmByName.containsKey(cross.getMaleParent().getReferenceValue())) {
                            father.setGermplasmName(cross.getMaleParent().getReferenceValue());
                        } else {
                            //TODO: Throw error if the user wants an error
                            addCross = false;
                        }
                    }
                    newCross.setParent2(father);
                }

                if (cross.getFemaleParent() == null && cross.getMaleParent() == null) {
                    addCross = false;
                }

                // We don't add the cross if the parents weren't found
                if (addCross) {
                    if (!crossByGermplasmName.containsKey(germplasm.getGermplasmName()) &&
                            germplasmPreview.getState() == ImportObjectState.NEW
                    ) {
                        crossByGermplasmName.put(germplasm.getGermplasmName(), new PendingImportObject<>(ImportObjectState.NEW, newCross));
                    } else if (!crossByGermplasmName.containsKey(germplasm.getGermplasmName())) {
                        //TODO: Need to search crosses. This is just a placeholder for now
                        crossByGermplasmName.put(germplasm.getGermplasmName(), new PendingImportObject<>(ImportObjectState.EXISTING, newCross));
                    }

                    mappedImportRow.setCross(crossByGermplasmName.get(germplasm.getGermplasmName()));
                }

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
}
