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

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.request.BrAPIObservationSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationSingleResponse;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.cache.ProgramCache;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.brapi.v2.model.BrAPIWSMIMEDataTypes.APPLICATION_JSON;

@Singleton
@Slf4j
public class BrAPIObservationDAO {

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final String referenceSource;
    private boolean runScheduledTasks;
    private final ProgramCache<BrAPIObservation> programObservationCache;

    @Inject
    public BrAPIObservationDAO(ProgramDAO programDAO,
                               ImportDAO importDAO,
                               BrAPIDAOUtil brAPIDAOUtil,
                               BrAPIEndpointProvider brAPIEndpointProvider,
                               @Property(name = "brapi.server.reference-source") String referenceSource,
                               @Property(name = "micronaut.bi.api.run-scheduled-tasks") boolean runScheduledTasks,
                               ProgramCacheProvider programCacheProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.referenceSource = referenceSource;
        this.runScheduledTasks = runScheduledTasks;
        this.programObservationCache = programCacheProvider.getProgramCache(this::fetchProgramObservations, BrAPIObservation.class);
    }

    @Scheduled(initialDelay = "3s")
    public void setup() {
        if(!runScheduledTasks) {
            return;
        }
        // Populate the observation cache for all programs on startup.
        log.debug("populating observation cache");
        List<Program> programs = programDAO.getActive();
        if (programs != null) {
            programObservationCache.populate(programs.stream().map(Program::getId).collect(Collectors.toList()));
        }
    }

    /**
     * Fetch formatted observations for this program.
     */
    private Map<String, BrAPIObservation> fetchProgramObservations(UUID programId) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        // Get the program.
        List<Program> programs = programDAO.get(programId);
        if (programs.size() != 1) {
            throw new InternalServerException("Program was not found for given id");
        }
        Program program = programs.get(0);

        // Set query params and make call.
        BrAPIObservationSearchRequest observationSearch = new BrAPIObservationSearchRequest();
        observationSearch.externalReferenceIds(List.of(programId.toString()));
        observationSearch.externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS)));
        return processObservationsForCache(brAPIDAOUtil.search(
                api::searchObservationsPost,
                api::searchObservationsSearchResultsDbIdGet,
                observationSearch
        ), program.getKey());
    }

    /**
     * Process a list of observations for insertion into the cache.
     */
    private Map<String, BrAPIObservation> processObservationsForCache(List<BrAPIObservation> programObservations, String programKey) {
        // Process programObservations in place (strip program key, etc.).
        processObservations(programKey, programObservations);
        // Build map.
        Map<String, BrAPIObservation> programObservationsMap = new HashMap<>();
        log.trace("processing observationUnits for cache: " + programObservations);
        for (BrAPIObservation observation: programObservations) {
            BrAPIExternalReference xref = observation
                    .getExternalReferences()
                    .stream()
                    .filter(reference -> String.format("%s/%s", referenceSource, ExternalReferenceSource.OBSERVATIONS.getName()).equals(reference.getReferenceSource()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No BI external reference found"));
            programObservationsMap.put(xref.getReferenceID(), observation);
        }
        return programObservationsMap;
    }

    /**
     * Process BrAPIObservations for use in DeltaBreed (e.g. strip program key).
     */
    private void processObservations(String programKey, List<BrAPIObservation> observations) {
        for (BrAPIObservation obs: observations) {
            // Strip program key from observationVariableName.
            if (StringUtils.isNotBlank(obs.getObservationVariableName())) {
                obs.setObservationVariableName(Utilities.removeProgramKey(obs.getObservationVariableName(), programKey));
            }
            // Strip program key and unknown info from germplasmName and observationUnitName.
            if (StringUtils.isNotBlank(obs.getGermplasmName())) {
                obs.setGermplasmName(Utilities.removeProgramKeyAndUnknownAdditionalData(obs.getGermplasmName(), programKey));
            }
            if (StringUtils.isNotBlank(obs.getObservationUnitName())) {
                obs.setObservationUnitName(Utilities.removeProgramKeyAndUnknownAdditionalData(obs.getObservationUnitName(), programKey));
            }
        }
    }

    /**
     * Get all observations for a program from the cache.
     */
    private Map<String, BrAPIObservation> getProgramObservations(UUID programId) throws ApiException {
        return programObservationCache.get(programId);
    }

    public List<BrAPIObservation> getObservationsByStudyName(List<String> studyNames, Program program) throws ApiException {
        if(studyNames.isEmpty()) {
            return Collections.emptyList();
        }
//        // Strip program key and unknown additional data from studyNames.
//        List<String> cleanedStudyNames = studyNames.stream()
//                .map(n -> Utilities.removeProgramKeyAndUnknownAdditionalData(n, program.getKey()))
//                .collect(Collectors.toList());
//        return getProgramObservations(program.getId()).values().stream()
//                .filter(o -> cleanedStudyNames.contains(o.getAdditionalInfo().get("studyName").getAsString()))
//                .collect(Collectors.toList());

        BrAPIObservationSearchRequest observationSearchRequest = new BrAPIObservationSearchRequest();
        observationSearchRequest.setProgramDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        observationSearchRequest.setStudyNames(new ArrayList<>(studyNames));
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationsApi.class);
        return brAPIDAOUtil.search(
                api::searchObservationsPost,
                (brAPIWSMIMEDataTypes, searchResultsDbId, page, pageSize) -> searchObservationsSearchResultsDbIdGet(program.getId(), searchResultsDbId, page, pageSize),
                observationSearchRequest
        );
    }

    public List<BrAPIObservation> getObservationsByTrialDbId(List<String> trialDbIds, Program program) throws ApiException {
        if(trialDbIds.isEmpty()) {
            return Collections.emptyList();
        }

        // TODO: get all observationUnits, then observation.
//        return getProgramObservations(program.getId()).values().stream()
//                .filter(o -> )
//                .collect(Collectors.toList());

        // TODO: remove old code ------
        BrAPIObservationSearchRequest observationSearchRequest = new BrAPIObservationSearchRequest();
        observationSearchRequest.setProgramDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        observationSearchRequest.setTrialDbIds(new ArrayList<>(trialDbIds));
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationsApi.class);
        return brAPIDAOUtil.search(
                api::searchObservationsPost,
                (brAPIWSMIMEDataTypes, searchResultsDbId, page, pageSize) -> searchObservationsSearchResultsDbIdGet(program.getId(), searchResultsDbId, page, pageSize),
                observationSearchRequest
        );
        // TODO: end old code ------
    }

    public List<BrAPIObservation> getObservationsByObservationUnitsAndVariables(Collection<String> ouDbIds, Collection<String> variableDbIds, Program program) throws ApiException {
        if(ouDbIds.isEmpty() || variableDbIds.isEmpty()) {
            return Collections.emptyList();
        }
//        return getProgramObservations(program.getId()).values().stream()
//                .filter(o -> ouDbIds.contains(o.getObservationDbId()) && variableDbIds.contains(o.getObservationVariableDbId()))
//                .collect(Collectors.toList());

        BrAPIObservationSearchRequest observationSearchRequest = new BrAPIObservationSearchRequest();
        observationSearchRequest.setProgramDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        observationSearchRequest.setObservationUnitDbIds(new ArrayList<>(ouDbIds));
        observationSearchRequest.setObservationVariableDbIds(new ArrayList<>(variableDbIds));
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), ObservationsApi.class);
        return brAPIDAOUtil.search(
                api::searchObservationsPost,
                (brAPIWSMIMEDataTypes, searchResultsDbId, page, pageSize) -> searchObservationsSearchResultsDbIdGet(program.getId(), searchResultsDbId, page, pageSize),
                observationSearchRequest
        );
    }

    @NotNull
    private ApiResponse<Pair<Optional<BrAPIObservationListResponse>, Optional<BrAPIAcceptedSearchResponse>>> searchObservationsSearchResultsDbIdGet(UUID programId, String searchResultsDbId, Integer page, Integer pageSize) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        return api.searchObservationsSearchResultsDbIdGet(APPLICATION_JSON, searchResultsDbId, page, pageSize);
    }

    public List<BrAPIObservation> createBrAPIObservations(List<BrAPIObservation> brAPIObservationList, UUID programId, ImportUpload upload) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        var program = programDAO.fetchOneById(programId);
        try {
            if (!brAPIObservationList.isEmpty()) {
                Callable<Map<String, BrAPIObservation>> postFunction = () -> {
                    List<BrAPIObservation> postResponse = brAPIDAOUtil.post(brAPIObservationList, upload, api::observationsPost, importDAO::update);
                    return processObservationsForCache(postResponse, program.getKey());
                };
                return programObservationCache.post(programId, postFunction);
            }
            return new ArrayList<>();
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    public BrAPIObservation updateBrAPIObservation(String dbId, BrAPIObservation observation, UUID programId) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        var program = programDAO.fetchOneById(programId);
        try {
            Callable<Map<String, BrAPIObservation>> postFunction = () -> {
                ApiResponse<BrAPIObservationSingleResponse> response = api.observationsObservationDbIdPut(dbId, observation);
                    if (response == null)
                    {
                        throw new ApiException("Response is null", 0, null, null);
                    }
                    BrAPIObservationSingleResponse body = response.getBody();
                    if (body == null) {
                        throw new ApiException("Response is missing body", 0, response.getHeaders(), null);
                    }
                    BrAPIObservation updatedObservation = body.getResult();
                    if (updatedObservation == null) {
                        throw new ApiException("Response body is missing result", 0, response.getHeaders(), response.getBody().toString());
                    }
                    return processObservationsForCache(List.of(updatedObservation), program.getKey());
            };
            return programObservationCache.post(programId, postFunction).get(0);
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }
}
