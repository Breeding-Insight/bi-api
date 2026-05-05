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
package org.breedinginsight.brapi.v2.dao.impl;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.TrialQueryParams;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPITrialSearchRequest;
import org.brapi.v2.model.core.response.BrAPITrialListResponse;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.cache.ProgramCache;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
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
@Context
@Singleton
public class BrAPITrialDAOImpl implements BrAPITrialDAO {
    private final ProgramCache<BrAPITrial> programExperimentCache;
    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final ProgramService programService;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final String referenceSource;
    private final boolean runScheduledTasks;

    @Inject
    public BrAPITrialDAOImpl(ProgramCacheProvider programCacheProvider,
                             ProgramDAO programDAO,
                             ImportDAO importDAO,
                             BrAPIDAOUtil brAPIDAOUtil,
                             ProgramService programService,
                             @Property(name = "brapi.server.reference-source") String referenceSource,
                             BrAPIEndpointProvider brAPIEndpointProvider,
                             @Property(name = "micronaut.bi.api.run-scheduled-tasks") boolean runScheduledTasks) {
        this.programExperimentCache = programCacheProvider.getProgramCache(this::fetchProgramExperiments, BrAPITrial.class);
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.programService = programService;
        this.referenceSource = referenceSource;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.runScheduledTasks = runScheduledTasks;
    }

    // TODO: Can be removed once cache instance is gone
    private Map<String, BrAPITrial> fetchProgramExperiments(UUID programId) throws ApiException {
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), TrialsApi.class);

        // Get the program
        List<Program> programs = programDAO.get(programId);
        if (programs.size() != 1) {
            throw new InternalServerException("Program was not found for given key");
        }
        Program program = programs.get(0);

        // Get the program experiments
        BrAPITrialSearchRequest trialSearch = new BrAPITrialSearchRequest();
        trialSearch.externalReferenceIDs(List.of(programId.toString()));
        trialSearch.externalReferenceSources(
                List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS))
        );
        List<BrAPITrial> programExperiments = brAPIDAOUtil.search(
                api::searchTrialsPost,
                api::searchTrialsSearchResultsDbIdGet,
                trialSearch
        );

        return experimentById(processExperimentsForDisplay(programExperiments, program.getKey()));
    }

    /**
     * This method requires a BI-API program.  If the BrAPIProgram inside this data model is not set,
     * this method will retrieve it.
     */
    private List<BrAPITrial> getBrAPITrialsUsingBrAPIProgram(Program program) throws ApiException {

        if (program == null || program.getId() == null) {
            throw new InternalServerException("BI-API Program or Program ID is null");
        }

        String brapiProgramDbId = Optional.of(program)
                .map(Program::getBrapiProgram)
                .map(BrAPIProgram::getProgramDbId)
                .orElse(null);

        if (brapiProgramDbId == null) {
            brapiProgramDbId = programDAO.getProgramBrAPI(program).getProgramDbId();
        }

        // TODO: Configurable max amount of trials per program, or paginate.

        TrialQueryParams trialQueryParams =
                TrialQueryParams.builder()
                        .programDbId(brapiProgramDbId)
                        .pageSize(1000)
                        .page(0)
                        .build();

        return getTrialsFromBrAPI(program, trialQueryParams);
    }

    private List<BrAPITrial> getTrialsFromBrAPI(Program program, TrialQueryParams trialQueryParams) throws ApiException {
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), TrialsApi.class);

        ApiResponse<BrAPITrialListResponse> response;

        try {
            response = api.trialsGet(trialQueryParams);
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw new InternalServerException("Error making BrAPI call", e);
        }

        if (response.getBody().getMetadata().getPagination().getTotalCount() > trialQueryParams.pageSize()) {
            throw new InternalServerException(String.format("More trials exist than requested [%s]", trialQueryParams));
        }

        List<BrAPITrial> trialsFromResponse = response.getBody().getResult().getData();

        return processExperimentsForDisplay(trialsFromResponse, program.getKey());
    }

    private Map<String, BrAPITrial> experimentById(List<BrAPITrial> trials) {
        Map<String, BrAPITrial> experimentById = new HashMap<>();
        for (BrAPITrial experiment: trials) {
            experimentById.put(experiment.getTrialDbId(), experiment);
        }
        return experimentById;
    }

    @Override
    public List<BrAPITrial> getTrialsByName(List<String> trialNames, Program program) throws ApiException {
        List<BrAPITrial> allTrialsForProgram = getBrAPITrialsUsingBrAPIProgram(program);

        List<BrAPITrial> trials = new ArrayList<>();
        if (allTrialsForProgram != null) {
            trials.addAll(allTrialsForProgram
                    .stream()
                    .filter(t -> trialNames.contains(t.getTrialName()))
                    .collect(Collectors.toList()));
        }

        return trials;
    }

    // TODO: Fix by using only code of inner callback and returning result
    @Override
    public List<BrAPITrial> createBrAPITrials(List<BrAPITrial> brAPITrialList, UUID programId, ImportUpload upload) {
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), TrialsApi.class);
        List<BrAPITrial> createdTrials = new ArrayList<>();
        try {
            if (!brAPITrialList.isEmpty()) {
                Callable<Map<String, BrAPITrial>> postCallback = () -> {
                    List<BrAPITrial> postedTrials = brAPIDAOUtil
                            .post(brAPITrialList, upload, api::trialsPost, importDAO::update);
                    return experimentById(postedTrials);
                };
                createdTrials.addAll(programExperimentCache.post(programId, postCallback));
            }

            return createdTrials;
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    @Override
    public BrAPITrial updateBrAPITrial(String trialDbId, BrAPITrial trial, UUID programId) {
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), TrialsApi.class);
        try {
            return brAPIDAOUtil.put(trialDbId, trial, api::trialsTrialDbIdPut);
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    @Override
    public List<BrAPITrial> getTrials(UUID programId) throws ApiException {
        Program program = programDAO.get(programId).get(0);
        program.setBrapiProgram(programDAO.getProgramBrAPI(program));

        return getBrAPITrialsUsingBrAPIProgram(program);
    }

    //Removes program key from trial name
    private List<BrAPITrial> processExperimentsForDisplay(
            List<BrAPITrial> trials,
            String programKey) throws ApiException {
        List<BrAPITrial> displayExperiments = new ArrayList<>();
        for (BrAPITrial trial: trials) {
            trial.setTrialName(Utilities.removeProgramKey(trial.getTrialName(), programKey, ""));
            displayExperiments.add(trial);
        }
        return displayExperiments;
    }

    @Override
    public Optional<BrAPITrial> getTrialById(UUID programId, UUID trialId) throws ApiException, DoesNotExistException {
        Program program = programDAO.get(programId).get(0);

        TrialQueryParams params = TrialQueryParams.builder()
                .trialDbId(trialId.toString())
                .pageSize(1)
                .page(0)
                .build();


        return getTrialsFromBrAPI(program, params).stream().findFirst();
    }

    @Override
    public List<BrAPITrial> getTrialsByDbIds(Collection<String> trialDbIds, Program program) throws ApiException {
        Collection<UUID> trialDbUUIDs = trialDbIds.stream().map(UUID::fromString).collect(Collectors.toList());

        return getTrialsByExperimentIds(trialDbUUIDs, program);
    }

    // TODO: ExperimentIds will = trialDbIds once cache is updated.  Update this method to get trials on dbId directly from brapi.
    @Override
    public List<BrAPITrial> getTrialsByExperimentIds(Collection<UUID> experimentIds, Program program) throws ApiException {
        if(experimentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> experimentIdsAsStrings = experimentIds.stream().map(UUID::toString).collect(Collectors.toList());

        BrAPITrialSearchRequest trialSearch = new BrAPITrialSearchRequest();
        trialSearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        trialSearch.trialDbIds(experimentIdsAsStrings);
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), TrialsApi.class);
        return processExperimentsForDisplay(brAPIDAOUtil.search(
                api::searchTrialsPost,
                api::searchTrialsSearchResultsDbIdGet,
                trialSearch
        ), program.getKey());
    }

    @Override
    public void deleteBrAPITrial(Program program, BrAPITrial trial, boolean hard) throws ApiException {
        // TODO: Switch to using the TrialsApi from the BrAPI client library once the delete endpoints are merged into it.
        var programBrAPIBaseUrl = brAPIDAOUtil.getProgramBrAPIBaseUrl(program.getId());
        var requestUrl = HttpUrl.parse(programBrAPIBaseUrl + "/trials/" + trial.getTrialDbId()).newBuilder();
        requestUrl.addQueryParameter("hardDelete", Boolean.toString(hard));
        HttpUrl url = requestUrl.build();
        var brapiRequest = new Request.Builder().url(url)
                .method("DELETE", null)
                .addHeader("Content-Type", "application/json")
                .build();

        brAPIDAOUtil.makeCall(brapiRequest);
    }

    // TODO: Remove when trial cache is removed.
    @Override
    public void repopulateCache(UUID programId) {
        this.programExperimentCache.invalidate(programId);
        this.programExperimentCache.populate(programId);
    }
}
