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

package org.breedinginsight.brapi.v2.dao;

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@Context
public class BrAPIGermplasmDAO {

    private final String BREEDING_METHOD_ID_KEY = "breedingMethodId";

    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    ProgramCache<String, BrAPIGermplasm> programGermplasmCache;

    @Inject
    public BrAPIGermplasmDAO(ProgramDAO programDAO, ImportDAO importDAO) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
    }

    @PostConstruct
    private void setup() {
        // Populate germplasm cache for all programs on startup
        List<UUID> programs = programDAO.getAll().stream().filter(Program::getActive).map(Program::getId).collect(Collectors.toList());
        programGermplasmCache = new ProgramCache<>(this::fetchProgramGermplasm, programs);
    }

    /**
     * Fetch the germplasm for this program, and process it to remove storage specific values
     * @param programId
     * @return this program's germplasm
     * @throws ApiException
     */
    public List<BrAPIGermplasm> getGermplasm(UUID programId) throws ApiException {
        return new ArrayList<>(programGermplasmCache.get(programId).values());
    }

    /**
     * Fetch the germplasm for this program as it was stored in the program's BrAPI server
     * @param programId
     * @return this program's germplasm as it is stored on its BrAPI server
     * @throws ApiException
     */
    public List<BrAPIGermplasm> getRawGermplasm(UUID programId) throws ApiException {
        Program program = new Program(programDAO.fetchOneById(programId));
        List<BrAPIGermplasm> cacheList = new ArrayList<>(programGermplasmCache.get(programId).values());
        return cacheList.stream().map(germplasm -> {
            germplasm.setGermplasmName(Utilities.appendProgramKey(germplasm.getDefaultDisplayName(), program.getKey(), germplasm.getAccessionNumber()));
            if(germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo()
                                                                 .has(BrAPIAdditionalInfoFields.GERMPLASM_RAW_PEDIGREE)) {
                germplasm.setPedigree(germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_RAW_PEDIGREE).getAsString());
            }

            return germplasm;
        }).collect(Collectors.toList());
    }


    /**
     * Fetch formatted germplasm for this program
     * @param programId
     * @return Map<Key = string representing germplasm UUID, value = formatted BrAPIGermplasm>
     * @throws ApiException
     */
    private Map<String, BrAPIGermplasm> fetchProgramGermplasm(UUID programId) throws ApiException {
        GermplasmApi api = new GermplasmApi(programDAO.getCoreClient(programId));

        // Set query params and make call
        BrAPIGermplasmSearchRequest germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.externalReferenceIDs(List.of(programId.toString()));
        germplasmSearch.externalReferenceSources(List.of(String.format("%s/programs", referenceSource)));
        return processGermplasmForDisplay(BrAPIDAOUtil.search(
                api::searchGermplasmPost,
                api::searchGermplasmSearchResultsDbIdGet,
                germplasmSearch
        ));
    }

    /**
     * Process germplasm into a format for display
     * @param programGermplasm
     * @return Map<Key = string representing germplasm UUID, value = formatted BrAPIGermplasm>
     * @throws ApiException
     */
    private Map<String,BrAPIGermplasm> processGermplasmForDisplay(List<BrAPIGermplasm> programGermplasm) {
        // Process the germplasm
        Map<String, BrAPIGermplasm> programGermplasmMap = new HashMap<>();
        log.debug("processing germ for display: " + programGermplasm);
        Map<String, BrAPIGermplasm> programGermplasmByFullName = new HashMap<>();
        for (BrAPIGermplasm germplasm: programGermplasm) {
            programGermplasmByFullName.put(germplasm.getGermplasmName(), germplasm);

            JsonObject additionalInfo = germplasm.getAdditionalInfo();
            if (additionalInfo != null && additionalInfo.has(BREEDING_METHOD_ID_KEY)) {
                germplasm.setBreedingMethodDbId(additionalInfo.get(BREEDING_METHOD_ID_KEY).getAsString());
            }

            if (germplasm.getDefaultDisplayName() != null) {
                germplasm.setGermplasmName(germplasm.getDefaultDisplayName());
            }
        }

        // Update pedigree string
        for (BrAPIGermplasm germplasm: programGermplasm) {
            if (germplasm.getPedigree() != null) {
                JsonObject additionalInfo = germplasm.getAdditionalInfo();
                if(additionalInfo == null) {
                    additionalInfo = new JsonObject();
                }
                additionalInfo.addProperty(BrAPIAdditionalInfoFields.GERMPLASM_RAW_PEDIGREE, germplasm.getPedigree());

                String newPedigreeString = "";
                String namePedigreeString = "";
                List<String> parents = Arrays.asList(germplasm.getPedigree().split("/"));
                if (parents.size() >= 1) {
                    if (programGermplasmByFullName.containsKey(parents.get(0))) {
                        newPedigreeString = programGermplasmByFullName.get(parents.get(0)).getAccessionNumber();
                        namePedigreeString = programGermplasmByFullName.get(parents.get(0)).getDefaultDisplayName();
                    }
                }
                if (parents.size() == 2) {
                    if (programGermplasmByFullName.containsKey(parents.get(1))) {
                        newPedigreeString += "/" + programGermplasmByFullName.get(parents.get(1)).getAccessionNumber();
                        namePedigreeString += "/" + programGermplasmByFullName.get(parents.get(1)).getDefaultDisplayName();
                    }
                }
                //For use in individual germplasm display
                additionalInfo.addProperty(BrAPIAdditionalInfoFields.GERMPLASM_PEDIGREE_BY_NAME, namePedigreeString);

                germplasm.setPedigree(newPedigreeString);
            }

            BrAPIExternalReference extRef = germplasm.getExternalReferences().stream().filter(reference -> referenceSource.equals(reference.getReferenceSource())).findFirst().orElseThrow(() -> new IllegalStateException("No BI external reference found"));
            String germplasmId = extRef.getReferenceID();
            programGermplasmMap.put(germplasmId, germplasm);
        }

        return programGermplasmMap;
    }

    public List<BrAPIGermplasm> importBrAPIGermplasm(List<BrAPIGermplasm> brAPIGermplasmList, UUID programId, ImportUpload upload) throws ApiException {
        GermplasmApi api = new GermplasmApi(programDAO.getCoreClient(programId));
        try {
            Callable<List<BrAPIGermplasm>> postFunction = () -> BrAPIDAOUtil.post(brAPIGermplasmList, upload, api::germplasmPost, importDAO::update);
            return programGermplasmCache.post(programId, postFunction);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage());
        }
    }

    public List<BrAPIGermplasm> getGermplasmByRawName(List<String> germplasmNames, UUID programId) throws ApiException {
        Program program = new Program(programDAO.fetchOneById(programId));
        return getGermplasm(programId)
                .stream()
                .filter(brAPIGermplasm -> germplasmNames.contains(Utilities.appendProgramKey(brAPIGermplasm.getGermplasmName(),program.getKey(),brAPIGermplasm.getAccessionNumber())))
                .collect(Collectors.toList());
    }

    public BrAPIGermplasm getGermplasmByUUID(String germplasmId, UUID programId) throws ApiException, DoesNotExistException {
        Map<String, BrAPIGermplasm> cache = programGermplasmCache.get(programId);
        BrAPIGermplasm germplasm = null;
        if (cache != null) {
            germplasm = cache.get(germplasmId);
        }
        if (germplasm == null) {
            throw new DoesNotExistException("UUID for this germplasm does not exist");
        }
        return germplasm;
    }

}
