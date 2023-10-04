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

import com.google.gson.JsonObject;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.daos.cache.ProgramCache;
import org.breedinginsight.daos.cache.ProgramCacheProvider;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrAPIStudyDAO {
    @Property(name = "brapi.server.reference-source")
    private String referenceSource;
    @Property(name = "micronaut.bi.api.run-scheduled-tasks")
    private boolean runScheduledTasks;

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final ProgramCache<BrAPIStudy> programStudyCache;


    @Inject
    public BrAPIStudyDAO(ProgramDAO programDAO, ImportDAO importDAO, BrAPIDAOUtil brAPIDAOUtil, BrAPIEndpointProvider brAPIEndpointProvider, ProgramCacheProvider programCacheProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.programStudyCache = programCacheProvider.getProgramCache(this::fetchProgramStudy, BrAPIStudy.class);
    }

    @Scheduled(initialDelay = "2s")
    public void setup() {
        if(!runScheduledTasks) {
            return;
        }
        // Populate study cache for all programs on startup
        log.debug("populating study cache");
        List<Program> programs = programDAO.getActive();
        if(programs != null) {
            programStudyCache.populate(programs.stream().map(Program::getId).collect(Collectors.toList()));
        }
    }


    /**
     * Fetch formatted study for this program
     * @param programId
     * @return Map - Key = string representing study UUID, value = formatted BrAPIStudy
     * @throws ApiException
     */
    private Map<String, BrAPIStudy> fetchProgramStudy(UUID programId) throws ApiException {
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), StudiesApi.class);
        // Get the program key
        List<Program> programs = programDAO.get(programId);
        if (programs.size() != 1) {
            throw new InternalServerException("Program was not found for given key");
        }
        Program program = programs.get(0);

        // Set query params and make call
        BrAPIStudySearchRequest studySearch = new BrAPIStudySearchRequest();
        studySearch.externalReferenceIDs(List.of(programId.toString()));
        studySearch.externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS)));
        return processStudyForDisplay(brAPIDAOUtil.search(
                api::searchStudiesPost,
                api::searchStudiesSearchResultsDbIdGet,
                studySearch
        ), program.getKey());
    }

    /**
     * Fetch the study for this program, and process it to remove storage specific values
     * @param programId
     * @return this program's study
     * @throws ApiException
     */
    public List<BrAPIStudy> getStudies(UUID programId) throws ApiException {
        return new ArrayList<>(programStudyCache.get(programId).values());
    }

    public Optional<BrAPIStudy> getStudyByName(String studyName, Program program) throws ApiException {
        List<BrAPIStudy> studies = getStudiesByName(List.of(studyName), program);
        return Utilities.getSingleOptional(studies);
    }

    public List<BrAPIStudy> getStudiesByName(List<String> studyNames, Program program) throws ApiException {
        if(studyNames.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPIStudySearchRequest studySearch = new BrAPIStudySearchRequest();
        studySearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        studySearch.studyNames(studyNames);
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), StudiesApi.class);
        return brAPIDAOUtil.search(
                api::searchStudiesPost,
                api::searchStudiesSearchResultsDbIdGet,
                studySearch
        );
    }

    public List<BrAPIStudy> getStudiesByExperimentID(@NotNull UUID experimentID, Program program ) throws ApiException {
        BrAPIStudySearchRequest studySearch = new BrAPIStudySearchRequest();
        studySearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        studySearch.addExternalReferenceIDsItem(experimentID.toString());
        studySearch.addExternalReferenceSourcesItem(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS));
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), StudiesApi.class);
        return brAPIDAOUtil.search(
                api::searchStudiesPost,
                api::searchStudiesSearchResultsDbIdGet,
                studySearch
        );
    }

    public List<BrAPIStudy> getStudiesByEnvironmentIds(@NotNull Collection<UUID> environmentIds, Program program ) throws ApiException {
        return programStudyCache.get(program.getId())
                                .entrySet()
                                .stream()
                                .filter(entry -> environmentIds.contains(UUID.fromString(entry.getKey())))
                                .map(Map.Entry::getValue)
                                .collect(Collectors.toList());
    }

    public List<BrAPIStudy> createBrAPIStudies(List<BrAPIStudy> brAPIStudyList, UUID programId, ImportUpload upload) throws ApiException {
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), StudiesApi.class);
        List<BrAPIStudy> createdStudies = new ArrayList<>();
        try {
            if (!brAPIStudyList.isEmpty()) {
                Callable<Map<String, BrAPIStudy>> postCallback = () -> {
                    List<BrAPIStudy> postedStudies = brAPIDAOUtil
                            .post(brAPIStudyList, upload, api::studiesPost, importDAO::update);
                    return environmentById(postedStudies);
                };
                createdStudies.addAll(programStudyCache.post(programId, postCallback));
            }

            return createdStudies;
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
    }

    /**
     * @return Map - Key = BI external reference ID, Value = BrAPIStudy
     * */
    private Map<String, BrAPIStudy> environmentById(List<BrAPIStudy> studies) {
        Map<String, BrAPIStudy> environmentById = new HashMap<>();
        for (BrAPIStudy environment: studies) {
            BrAPIExternalReference xref = environment
                    .getExternalReferences()
                    .stream()
                    .filter(reference -> String.format("%s/%s", referenceSource, ExternalReferenceSource.STUDIES).equalsIgnoreCase(reference.getReferenceSource()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No BI external reference found"));
            environmentById.put(xref.getReferenceID(), environment);
        }
        return environmentById;
    }

    public List<BrAPIStudy> getStudiesByStudyDbId(Collection<String> studyDbIds, Program program) throws ApiException {
        if(studyDbIds.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPIStudySearchRequest studySearch = new BrAPIStudySearchRequest();
        studySearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        studySearch.studyDbIds(new ArrayList<>(studyDbIds));
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), StudiesApi.class);
        return brAPIDAOUtil.search(
                api::searchStudiesPost,
                api::searchStudiesSearchResultsDbIdGet,
                studySearch
        );
    }

    public Optional<BrAPIStudy> getStudyByDbId(String studyDbId, Program program) throws ApiException {
        List<BrAPIStudy> studies = getStudiesByStudyDbId(List.of(studyDbId), program);

        return Utilities.getSingleOptional(studies);
    }

    public Optional<BrAPIStudy> getStudyByEnvironmentId(UUID environmentId, Program program) throws ApiException {
        List<BrAPIStudy> studies = getStudiesByEnvironmentIds(List.of(environmentId), program);

        return Utilities.getSingleOptional(studies);
    }


    /**
     * Process study into a format for display
     * @param programStudy
     * @return Map - Key = string representing study UUID, value = formatted BrAPIStudy
     */
    private Map<String,BrAPIStudy> processStudyForDisplay(List<BrAPIStudy> programStudy, String programKey) {
        Map<String, BrAPIStudy> programStudyMap = new HashMap<>();
        log.trace("processing study for display: " + programStudy);
        for (BrAPIStudy study: programStudy) {
            // Remove program key from studyName, trialName and locationName.
            if (study.getStudyName() != null) {
                // Study name is appended with experiment sequence number in addition to program key.
                study.setStudyName(Utilities.removeProgramKeyAndUnknownAdditionalData(study.getStudyName(), programKey));
            }
            if (study.getTrialName() != null) {
                study.setTrialName(Utilities.removeProgramKey(study.getTrialName(), programKey));
            }
            if (study.getLocationName() != null) {
                study.setLocationName(Utilities.removeProgramKey(study.getLocationName(), programKey));
            }
        }

        String refSource = Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.STUDIES);
        // Add to map.
        for (BrAPIStudy study: programStudy) {
            JsonObject additionalInfo = study.getAdditionalInfo();
            if(additionalInfo == null) {
                additionalInfo = new JsonObject();
                study.setAdditionalInfo(additionalInfo);
            }

            BrAPIExternalReference extRef = study.getExternalReferences().stream()
                    .filter(reference -> reference.getReferenceSource().equals(refSource))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No BI external reference found"));
            String studyId = extRef.getReferenceID();
            programStudyMap.put(studyId, study);
        }

        return programStudyMap;
    }
}