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
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.BrAPIGermplasmSynonyms;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.brapi.v2.model.germ.response.BrAPIGermplasmSingleResponse;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.cache.ProgramCache;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@Context
public class BrAPIGermplasmDAO {

    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Property(name = "micronaut.bi.api.run-scheduled-tasks")
    private boolean runScheduledTasks;

    private final ProgramCache<BrAPIGermplasm> programGermplasmCache;

    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIGermplasmDAO(ProgramDAO programDAO, ImportDAO importDAO, BrAPIDAOUtil brAPIDAOUtil, ProgramCacheProvider programCacheProvider, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.programGermplasmCache = programCacheProvider.getProgramCache(this::fetchProgramGermplasm, BrAPIGermplasm.class);
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    @Scheduled(initialDelay = "2s")
    public void setup() {
        if(!runScheduledTasks) {
            return;
        }
        // Populate germplasm cache for all programs on startup
        log.debug("populating germplasm cache");
        List<Program> programs = programDAO.getActive();
        if(programs != null) {
            programGermplasmCache.populate(programs.stream().map(Program::getId).collect(Collectors.toList()));
        }
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
            if(germplasm.getAdditionalInfo() != null && germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_RAW_PEDIGREE)
                    && !(germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.GERMPLASM_RAW_PEDIGREE).isJsonNull())) {
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
        GermplasmApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), GermplasmApi.class);
        // Get the program key
        List<Program> programs = programDAO.get(programId);
        if (programs.size() != 1) {
            throw new InternalServerException("Program was not found for given key");
        }
        Program program = programs.get(0);

        // Set query params and make call
        BrAPIGermplasmSearchRequest germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.externalReferenceIDs(List.of(programId.toString()));
        germplasmSearch.externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS)));
        return processGermplasmForDisplay(brAPIDAOUtil.search(
                api::searchGermplasmPost,
                api::searchGermplasmSearchResultsDbIdGet,
                germplasmSearch
        ), program.getKey());
    }

    /**
     * Process germplasm into a format for display
     * @param programGermplasm
     * @return Map<Key = string representing germplasm UUID, value = formatted BrAPIGermplasm>
     * @throws ApiException
     */
    private Map<String,BrAPIGermplasm> processGermplasmForDisplay(List<BrAPIGermplasm> programGermplasm, String programKey) {
        // Process the germplasm
        Map<String, BrAPIGermplasm> programGermplasmMap = new HashMap<>();
        log.trace("processing germ for display: " + programGermplasm);
        Map<String, BrAPIGermplasm> programGermplasmByFullName = new HashMap<>();
        for (BrAPIGermplasm germplasm: programGermplasm) {
            programGermplasmByFullName.put(germplasm.getGermplasmName(), germplasm);

            JsonObject additionalInfo = germplasm.getAdditionalInfo();
            if (additionalInfo != null && additionalInfo.has(BrAPIAdditionalInfoFields.GERMPLASM_BREEDING_METHOD_ID)) {
                germplasm.setBreedingMethodDbId(additionalInfo.get(BrAPIAdditionalInfoFields.GERMPLASM_BREEDING_METHOD_ID).getAsString());
            }

            if (germplasm.getDefaultDisplayName() != null) {
                germplasm.setGermplasmName(germplasm.getDefaultDisplayName());
            }

            // Remove program key
            if (germplasm.getSynonyms() != null && !germplasm.getSynonyms().isEmpty()) {
                for (BrAPIGermplasmSynonyms synonym: germplasm.getSynonyms()) {
                    String newSynonym = Utilities.removeProgramKey(synonym.getSynonym(), programKey, germplasm.getAccessionNumber());
                    synonym.setSynonym(newSynonym);
                }
            }
        }

        // Update pedigree string
        for (BrAPIGermplasm germplasm: programGermplasm) {
                JsonObject additionalInfo = germplasm.getAdditionalInfo();
                if(additionalInfo == null) {
                    additionalInfo = new JsonObject();
                    germplasm.setAdditionalInfo(additionalInfo);
                }

                String pedigree = processBreedbasePedigree(germplasm.getPedigree());
                additionalInfo.addProperty(BrAPIAdditionalInfoFields.GERMPLASM_RAW_PEDIGREE, pedigree);

                String newPedigreeString = "";
                String namePedigreeString = "";
                String uuidPedigreeString = "";
                List<String> parents = Arrays.asList("","");
                if (germplasm.getPedigree() != null) {
                    parents = Arrays.asList(germplasm.getPedigree().split("/"));
                }
                if (parents.size() >= 1) {
                    if (programGermplasmByFullName.containsKey(parents.get(0))) {
                        String femaleParentAccessionNumber = programGermplasmByFullName.get(parents.get(0)).getAccessionNumber();
                        newPedigreeString = femaleParentAccessionNumber;
                        namePedigreeString = programGermplasmByFullName.get(parents.get(0)).getDefaultDisplayName();
                        uuidPedigreeString = programGermplasmByFullName.get(parents.get(0)).getExternalReferences().
                                stream().filter(ref -> ref.getReferenceSource().equals(referenceSource)).
                                map(ref -> ref.getReferenceID()).findFirst().orElse("");
                        additionalInfo.addProperty(BrAPIAdditionalInfoFields.GERMPLASM_FEMALE_PARENT_GID, femaleParentAccessionNumber);
                    } else if (additionalInfo.has(BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN) && additionalInfo.get(BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN).getAsBoolean()) {
                        namePedigreeString = "Unknown";
                    }
                }
                if (parents.size() == 2) {
                    if (programGermplasmByFullName.containsKey(parents.get(1))) {
                        String maleParentAccessionNumber = programGermplasmByFullName.get(parents.get(1)).getAccessionNumber();
                        newPedigreeString += "/" + maleParentAccessionNumber;
                        namePedigreeString += "/" + programGermplasmByFullName.get(parents.get(1)).getDefaultDisplayName();
                        uuidPedigreeString += "/" + programGermplasmByFullName.get(parents.get(1)).getExternalReferences().
                                stream().filter(ref -> ref.getReferenceSource().equals(referenceSource)).
                                map(ref -> ref.getReferenceID()).findFirst().orElse("");
                        additionalInfo.addProperty(BrAPIAdditionalInfoFields.GERMPLASM_MALE_PARENT_GID, maleParentAccessionNumber);
                    }
                }
                //Add Unknown germplasm for display
                if (additionalInfo.has(BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN) && additionalInfo.get(BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN).getAsBoolean()) {
                    namePedigreeString += "/Unknown";
                }
                //For use in individual germplasm display
                additionalInfo.addProperty(BrAPIAdditionalInfoFields.GERMPLASM_PEDIGREE_BY_NAME, namePedigreeString);
                additionalInfo.addProperty(BrAPIAdditionalInfoFields.GERMPLASM_PEDIGREE_BY_UUID, uuidPedigreeString);

                germplasm.setPedigree(newPedigreeString);

            BrAPIExternalReference extRef = germplasm.getExternalReferences().stream().filter(reference -> referenceSource.equals(reference.getReferenceSource())).findFirst().orElseThrow(() -> new IllegalStateException("No BI external reference found"));
            String germplasmId = extRef.getReferenceID();
            programGermplasmMap.put(germplasmId, germplasm);
        }

        return programGermplasmMap;
    }

    // TODO: hack for now, probably should update breedbase
    // Breedbase will return NA/NA for no pedigree or NA/father, mother/NA
    // strip NAs before saving RAW_PEDIGREE, if there was a germplasm with name NA it would be in format NA [program key]
    // so that case should be ok if we just strip NA/NA, NA/, or /NA<\0>
    private String processBreedbasePedigree(String pedigree) {

        if (pedigree != null) {
            if (pedigree.equals("NA/NA")) {
                return "";
            }

            // Technically processGermplasmForDisplay should handle ok without stripping these NAs but will strip anyways
            // for consistency.
            // We only allow the /NA case for single parent as we require a female parent in the pedigree
            // keep the leading slash, will be handled by processGermplasmForDisplay
            if (pedigree.endsWith("/NA")) {
                return pedigree.substring(0, pedigree.length()-2);
            }

            // shouldn't have this case in our data but just in case
            if (pedigree.startsWith("NA/")) {
                return pedigree.substring(2);
            }
        }
        return "";
    }

    public List<BrAPIGermplasm> createBrAPIGermplasm(List<BrAPIGermplasm> postBrAPIGermplasmList, UUID programId, ImportUpload upload) {
        GermplasmApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), GermplasmApi.class);
        var program = programDAO.fetchOneById(programId);
        Callable<Map<String, BrAPIGermplasm>> postFunction = null;
        try {
            if (!postBrAPIGermplasmList.isEmpty()) {
                postFunction = () -> {
                    List<BrAPIGermplasm> postResponse = brAPIDAOUtil.post(postBrAPIGermplasmList, upload, api::germplasmPost, importDAO::update);
                    return processGermplasmForDisplay(postResponse, program.getKey());
                };
            }
            return programGermplasmCache.post(programId, postFunction);
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    public List<BrAPIGermplasm> updateBrAPIGermplasm(List<BrAPIGermplasm> putBrAPIGermplasmList, UUID programId, ImportUpload upload) {
        GermplasmApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), GermplasmApi.class);
        var program = programDAO.fetchOneById(programId);
        Callable<Map<String, BrAPIGermplasm>> postFunction = null;
        try {
            if (!putBrAPIGermplasmList.isEmpty()) {
                postFunction = () -> {
                    List<BrAPIGermplasm> postResponse = putGermplasm(putBrAPIGermplasmList, api);
                    return processGermplasmForDisplay(postResponse, program.getKey());
                };
            }
            return programGermplasmCache.post(programId, postFunction);
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
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

    public Optional<BrAPIGermplasm> getGermplasmByDBID(String germplasmDbId, UUID programId) throws ApiException {
        Map<String, BrAPIGermplasm> cache = programGermplasmCache.get(programId);
        //key is UUID, want to filter by DBID
        BrAPIGermplasm germplasm = null;
        if (cache != null) {
            germplasm = cache.values().stream().filter(x -> x.getGermplasmDbId().equals(germplasmDbId)).collect(Collectors.toList()).get(0);
        }
        return Optional.ofNullable(germplasm);
    }

    public List<BrAPIGermplasm> getGermplasmsByDBID(Collection<String> germplasmDbIds, UUID programId) throws ApiException {
        Map<String, BrAPIGermplasm> cache = programGermplasmCache.get(programId);
        //key is UUID, want to filter by DBID
        List<BrAPIGermplasm> germplasm = new ArrayList<>();
        if (cache != null) {
            germplasm = cache.values().stream().filter(x -> germplasmDbIds.contains(x.getGermplasmDbId())).collect(Collectors.toList());
        }
        return germplasm;
    }

    public List<BrAPIGermplasm> putGermplasm(List<BrAPIGermplasm> germplasmList, GermplasmApi api) throws ApiException {
        List<BrAPIGermplasm> listResult = new ArrayList<>();

        // TODO: temporary until generic BrAPIDAOUtil code is written
        // generic code should handle importer progress updates
        for (BrAPIGermplasm germplasm : germplasmList) {
            ApiResponse<BrAPIGermplasmSingleResponse> response = api.germplasmGermplasmDbIdPut(germplasm.getGermplasmDbId(), germplasm);
            listResult.add(response.getBody().getResult());
        }

        return listResult;
    }
}
