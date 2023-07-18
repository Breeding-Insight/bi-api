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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest;
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
import java.util.stream.Collectors;

@Slf4j
@Singleton
@Context
public class BrAPIStudyDAO {

    private final ProgramDAO programDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Property(name = "micronaut.bi.api.run-scheduled-tasks")
    private boolean runScheduledTasks;

    private final ProgramCache<BrAPIStudy> programStudyCache;

    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIStudyDAO(ProgramDAO programDAO, BrAPIDAOUtil brAPIDAOUtil, ProgramCacheProvider programCacheProvider, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.programStudyCache = programCacheProvider.getProgramCache(this::fetchProgramStudy, BrAPIStudy.class);
        this.brAPIEndpointProvider = brAPIEndpointProvider;
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
     * Fetch the study for this program, and process it to remove storage specific values
     * @param programId
     * @return this program's study
     * @throws ApiException
     */
    public List<BrAPIStudy> getStudies(UUID programId) throws ApiException {
        return new ArrayList<>(programStudyCache.get(programId).values());
    }

    /**
     * Fetch formatted study for this program
     * @param programId
     * @return Map<Key = string representing study UUID, value = formatted BrAPIStudy>
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
     * Process study into a format for display
     * @param programStudy
     * @return Map<Key = string representing study UUID, value = formatted BrAPIStudy>
     * @throws ApiException
     */
    private Map<String,BrAPIStudy> processStudyForDisplay(List<BrAPIStudy> programStudy, String programKey) {
        // Process the study
        Map<String, BrAPIStudy> programStudyMap = new HashMap<>();
        log.trace("processing germ for display: " + programStudy);
        Map<String, BrAPIStudy> programStudyByFullName = new HashMap<>();
        for (BrAPIStudy study: programStudy) {
            programStudyByFullName.put(study.getStudyName(), study);
            // Remove program key from studyName, trialName and locationName.
            if (study.getStudyName() != null) {
                // Study name is appended with program key and experiment sequence number, need to remove.
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
