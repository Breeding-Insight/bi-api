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
package org.breedinginsight.brapps.importer.daos;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.TrialsApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.request.BrAPITrialSearchRequest;
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

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Slf4j
@Context
@Singleton
public class BrAPITrialDAO {
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;
    private final ProgramCache<BrAPITrial> programExperimentCache;
    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final ProgramService programService;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final String referenceSource;

    @Inject
    public BrAPITrialDAO(ProgramCacheProvider programCacheProvider, ProgramDAO programDAO, ImportDAO importDAO, BrAPIDAOUtil brAPIDAOUtil, ProgramService programService, @Property(name = "brapi.server.reference-source") String referenceSource, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programExperimentCache = programCacheProvider.getProgramCache(this::fetchProgramExperiments, BrAPITrial.class);
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.programService = programService;
        this.referenceSource = referenceSource;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }


    @PostConstruct
    public void setup() {
        // Populate the experiment cache for all programs on startup
        log.debug("populating experiment cache");
        List<Program> programs = programDAO.getActive();
        if (programs != null) {
            programExperimentCache.populate(programs.stream().map(Program::getId).collect(Collectors.toList()));
        }
    }

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

    private Map<String, BrAPITrial> experimentById(List<BrAPITrial> trials) {
        Map<String, BrAPITrial> experimentById = new HashMap<>();
        for (BrAPITrial experiment: trials) {
            BrAPIExternalReference xref = experiment
                    .getExternalReferences()
                    .stream()
                    .filter(reference -> String.format("%s/%s", referenceSource, ExternalReferenceSource.TRIALS).equalsIgnoreCase(reference.getReferenceSource()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No BI external reference found"));
            experimentById.put(xref.getReferenceID(), experiment);
        }
        return experimentById;
    }

    public List<BrAPITrial> getTrialsByName(List<String> trialNames, Program program) throws ApiException {
        Map<String, BrAPITrial> cache = programExperimentCache.get(program.getId());
        List<BrAPITrial> trials = new ArrayList<>();
        if (cache != null) {

            // TODO: replace with more performant cache search, e.g. RediSearch
            trials.addAll(cache
                    .values()
                    .stream()
                    .filter(t -> trialNames.contains(t.getTrialName()))
                    .collect(Collectors.toList()));
        }

        return trials;
    }

    private List<BrAPITrial> getTrialsByExRef(String referenceSource, String referenceId, Program program) throws ApiException {
        Map<String, BrAPITrial> cache = programExperimentCache.get(program.getId());
        List<BrAPITrial> trials = new ArrayList<>();
        if (cache != null) {
            trials.addAll(cache
                    .values()
                    .stream()
                    .filter(t -> {
                        BrAPIExternalReference xref = t
                                .getExternalReferences()
                                .stream()
                                .filter(reference -> String.format("%s/%s", referenceSource, ExternalReferenceSource.TRIALS)
                                        .equalsIgnoreCase(reference.getReferenceSource()))
                                .findFirst().orElseThrow(() -> new IllegalStateException("No BI trial external reference found"));
                        return referenceId.equals(xref.getReferenceID());
                    })
                    .collect(Collectors.toList()));
        }

        return trials;
    }

    public List<BrAPITrial> createBrAPITrials(List<BrAPITrial> brAPITrialList, UUID programId, ImportUpload upload)
            throws ApiException {
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
    public BrAPITrial updateBrAPITrial(String trialDbId, BrAPITrial trial, UUID programId) throws ApiException {
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), TrialsApi.class);
        BrAPITrial updatedTrial = null;
        try {
            if (trial != null && !trialDbId.isBlank()) {
                Callable<Map<String, BrAPITrial>> putCallback = () -> {
                    BrAPITrial putTrial = brAPIDAOUtil.put(trialDbId, trial, api::trialsTrialDbIdPut);
                    return experimentById(List.of(putTrial));
                };
                List<BrAPITrial> cachedUpdates = programExperimentCache.post(programId, putCallback);
                if (cachedUpdates.isEmpty()) {
                    throw new Exception();
                }
                updatedTrial = cachedUpdates.get(0);
            }

            return updatedTrial;
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    public List<BrAPITrial> getTrials(UUID programId) throws ApiException {
        return new ArrayList<>(programExperimentCache.get(programId).values());
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

    public Optional<BrAPITrial> getTrialById(UUID programId, UUID trialId) throws ApiException, DoesNotExistException {
        Map<String, BrAPITrial> cache = programExperimentCache.get(programId);
        BrAPITrial trial = null;
        if (cache != null) {
            trial = cache.get(trialId.toString());
        }

        return Optional.ofNullable(trial);
    }

    public Optional<BrAPITrial> getTrialByDbId(String trialDbId, Program program) throws ApiException {
        List<BrAPITrial> trials = getTrialsByDbIds(List.of(trialDbId), program);
        return Utilities.getSingleOptional(trials);
    }

    public List<BrAPITrial> getTrialsByDbIds(Collection<String> trialDbIds, Program program) throws ApiException {
        Map<String, BrAPITrial> cache = programExperimentCache.get(program.getId());
        List<BrAPITrial> trials = new ArrayList<>();
        if (cache != null) {
            trials.addAll(cache
                    .values()
                    .stream()
                    .filter(t -> trialDbIds.contains(t.getTrialDbId()))
                    .collect(Collectors.toList()));
        }

        return trials;
    }
    public List<BrAPITrial> getTrialsByExperimentIds(Collection<UUID> experimentIds, Program program) throws ApiException {
        if(experimentIds.isEmpty()) {
            return Collections.emptyList();
        }
        BrAPITrialSearchRequest trialSearch = new BrAPITrialSearchRequest();
        trialSearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        trialSearch.externalReferenceSources(List.of(referenceSource + "/" + ExternalReferenceSource.TRIALS.getName()));
        trialSearch.externalReferenceIDs(experimentIds.stream().map(id -> id.toString()).collect(Collectors.toList()));
        TrialsApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), TrialsApi.class);
        return brAPIDAOUtil.search(
                api::searchTrialsPost,
                api::searchTrialsSearchResultsDbIdGet,
                trialSearch
        );
    }
}


