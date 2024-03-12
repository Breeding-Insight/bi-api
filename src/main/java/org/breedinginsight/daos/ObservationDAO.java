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
package org.breedinginsight.daos;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.phenotype.ObservationQueryParams;
import org.brapi.client.v2.modules.phenotype.ObservationsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.request.BrAPIObservationSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.cache.ProgramCache;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;


@Singleton
@Slf4j
public class ObservationDAO {
    private final BrAPIDAOUtil brAPIDAOUtil;
    private ProgramDAO programDAO;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final String referenceSource;
    private boolean runScheduledTasks;
    private final ProgramCache<BrAPIObservation> programObservationCache;

    @Inject
    public ObservationDAO(BrAPIDAOUtil brAPIDAOUtil,
                          ProgramDAO programDAO,
                          BrAPIEndpointProvider brAPIEndpointProvider,
                          @Property(name = "brapi.server.reference-source") String referenceSource,
                          @Property(name = "micronaut.bi.api.run-scheduled-tasks") boolean runScheduledTasks,
                          ProgramCacheProvider programCacheProvider) {
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.programDAO = programDAO;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.referenceSource = referenceSource;
        this.runScheduledTasks = runScheduledTasks;
        this.programObservationCache = programCacheProvider.getProgramCache(this::fetchProgramObservations, BrAPIObservation.class);
    }

    @Scheduled(initialDelay = "2s")
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
     * Fetch formatted observation for this program.
     * @param programId
     * @return Map<Key = string representing observation UUID, value = formatted BrAPIObservation>
     * @throws ApiException
     */
    private Map<String, BrAPIObservation> fetchProgramObservations(UUID programId) throws ApiException {
        ObservationsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), ObservationsApi.class);
        // Get the program key.
        List<Program> programs = programDAO.get(programId);
        if (programs.size() != 1) {
            throw new InternalServerException("Program was not found for given key");
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

    // TODO: use program key, strip from observationUnitName, etc.
    /**
     * Process a list of observations for insertion into the cache.
     * @param programObservations
     * @param programKey
     * @return
     */
    private Map<String, BrAPIObservation> processObservationsForCache(List<BrAPIObservation> programObservations, String programKey) {
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

    public List<BrAPIObservation> getObservationsByVariableDbId(String observationVariableDbId, UUID programId) {

        ApiResponse<BrAPIObservationListResponse> brapiObservations;
        ObservationQueryParams observationsRequest = new ObservationQueryParams()
                .observationVariableDbId(observationVariableDbId);
        try {
            BrAPIClient client = programDAO.getCoreClient(programId);
            ObservationsApi api = brAPIEndpointProvider.get(client, ObservationsApi.class);
            brapiObservations = api.observationsGet(observationsRequest);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

        return brapiObservations.getBody().getResult().getData();
    }

    // search by ObservationVariableDbIds
    public List<BrAPIObservation> getObservationsByVariableDbIds(List<String> observationVariableDbIds, UUID programId) {
        if(observationVariableDbIds.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            BrAPIObservationSearchRequest request = new BrAPIObservationSearchRequest()
                    .observationVariableDbIds(observationVariableDbIds);

            BrAPIClient client = programDAO.getCoreClient(programId);
            ObservationsApi api = brAPIEndpointProvider.get(client, ObservationsApi.class);
            return brAPIDAOUtil.search(
                    api::searchObservationsPost,
                    api::searchObservationsSearchResultsDbIdGet,
                    request
            );
        } catch (ApiException e) {
            throw new InternalServerException("Observations brapi search error", e);
        }

    }

    public List<BrAPIObservation> getObservationsByVariableAndBrAPIProgram(String brapiProgramId, UUID programId, List<String> observationVariableDbIds) {
        if(observationVariableDbIds.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            BrAPIObservationSearchRequest request = new BrAPIObservationSearchRequest()
                    .observationVariableDbIds(observationVariableDbIds)
                    .programDbIds(List.of(brapiProgramId));

            BrAPIClient client = programDAO.getCoreClient(programId);
            ObservationsApi api = brAPIEndpointProvider.get(client, ObservationsApi.class);
            return brAPIDAOUtil.search(
                    api::searchObservationsPost,
                    api::searchObservationsSearchResultsDbIdGet,
                    request
            );
        } catch (ApiException e) {
            throw new InternalServerException("Observations brapi search error", e);
        }

    }

}
