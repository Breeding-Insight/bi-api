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
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.StudyQueryParams;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest;
import org.brapi.v2.model.core.response.BrAPIStudyListResponse;
import org.breedinginsight.brapi.v2.model.request.query.StudyQuery;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class BrAPIStudyDAO {
    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Property(name = "brapi.cache.fetch-page-size")
    private int brapiMaxPageSize;

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIStudyDAO(
            ProgramDAO programDAO,
            ImportDAO importDAO,
            BrAPIDAOUtil brAPIDAOUtil,
            BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    /**
     * Fetch the study for this program, and process it to remove storage specific values
     * @param programId
     * @return this program's study
     * @throws ApiException
     */
    public List<BrAPIStudy> getStudies(UUID programId) throws ApiException {
        Program program = programDAO.get(programId)
                .stream()
                .findFirst()
                .orElseThrow();

        return getBrAPIStudiesUsingBrAPIProgramId(program);
    }

    public BrAPIStudyListResponse brapiStudySearch(Program program, StudyQuery studyQuery) throws ApiException {
        return brapiStudySearch(program, Collections.emptyList(), studyQuery);
    }

    public BrAPIStudyListResponse brapiStudySearch(Program program, List<UUID> brapiTrialIds,
                                                   StudyQuery studyQuery) throws ApiException {
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), StudiesApi.class);

        BrAPIStudySearchRequest brAPIStudySearchRequest = buildSearchRequest(program, brapiTrialIds, studyQuery);

        BrAPIStudyListResponse brAPIResponse = brAPIDAOUtil.simpleSearch(api::searchStudiesPost, brAPIStudySearchRequest);

        List<BrAPIStudy> processedStudies = new ArrayList<>(processStudyForDisplay(brAPIDAOUtil.getListResult(brAPIResponse), program.getKey()).values());

        brAPIResponse.getResult().setData(processedStudies);

        return brAPIResponse;
    }

    private BrAPIStudySearchRequest buildSearchRequest(Program program, List<UUID> brapiTrialIds, StudyQuery studyQuery) {
        BrAPIProgram brAPIProgram = programDAO.getProgramBrAPI(program);

        if (brAPIProgram == null || brAPIProgram.getProgramDbId() == null) {
            throw new InternalServerException(String.format("BI program with id [%s] not found in BrAPI db", program.getId()));
        }

        BrAPIStudySearchRequest searchRequest = new BrAPIStudySearchRequest();

        searchRequest.programDbIds(List.of(brAPIProgram.getProgramDbId()));

        if (brapiTrialIds != null && !brapiTrialIds.isEmpty()) {
            searchRequest.setTrialDbIds(brapiTrialIds.stream().map(UUID::toString).collect(Collectors.toList()));
        }

        brAPIDAOUtil.setGenericSearchParameters(searchRequest, studyQuery);

        return searchRequest;
    }

    private List<BrAPIStudy> getBrAPIStudiesUsingBrAPIProgramId(Program program) throws ApiException {
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

        StudyQueryParams studyQueryParams = StudyQueryParams.builder()
                .programDbId(brapiProgramDbId)
                .page(0)
                .pageSize(brapiMaxPageSize)
                .build();

        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), StudiesApi.class);

        List<BrAPIStudy> result = brAPIDAOUtil.get(api::studiesGet, studyQueryParams);

        return new ArrayList<>(processStudyForDisplay(result, program.getKey()).values());
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

    public List<BrAPIStudy> getStudiesByBrAPITrialExRefId(@NotNull UUID brapiTrialExRefId, Program program) throws ApiException {
        // TODO: If external references are removed for trial for studies, this method should look up on trialDbId, and the trialDbId should be passed through. [BI-2933]
        BrAPIStudySearchRequest studySearch = new BrAPIStudySearchRequest();
        studySearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        studySearch.addExternalReferenceIdsItem(brapiTrialExRefId.toString());
        studySearch.addExternalReferenceSourcesItem(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS));
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), StudiesApi.class);
        return brAPIDAOUtil.search(
                api::searchStudiesPost,
                api::searchStudiesSearchResultsDbIdGet,
                studySearch
        );
    }

    /**
     * Get a list of studies by a list of BI-assigned experiment UUIDs within a program.
     * @param experimentIds a list of BI-assigned experiment UUIDs.
     * @param program the program.
     * @return a list of BrAPIStudies.
     */
    public List<BrAPIStudy> getStudiesByExperimentIds(@NotNull Collection<UUID> experimentIds, Program program) throws ApiException {
        BrAPIStudySearchRequest studySearch = new BrAPIStudySearchRequest();
        studySearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        studySearch.trialDbIds(experimentIds.stream().map(UUID::toString).collect(Collectors.toList()));
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), StudiesApi.class);
        return new ArrayList<>(processStudyForDisplay(brAPIDAOUtil.search(
                api::searchStudiesPost,
                api::searchStudiesSearchResultsDbIdGet,
                studySearch
        ), program.getKey()).values());
    }

    public List<BrAPIStudy> createBrAPIStudies(List<BrAPIStudy> brAPIStudyList, UUID programId, ImportUpload upload) throws ApiException {
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), StudiesApi.class);

        try {
            if (!brAPIStudyList.isEmpty()) {
                //Create studies directly through BrAPI.
                List<BrAPIStudy> postedStudies = brAPIDAOUtil.post(brAPIStudyList, upload, api::studiesPost, importDAO::update);

                // Return the BrAPI response without updating Redis.
                return postedStudies;
            }

            // Preserve the existing empty-input behavior.
            return new ArrayList<>();
        } catch (Exception e) {
            throw new InternalServerException("Unknown error has occurred: " + e.getMessage(), e);
        }
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

    /**
     * Process study into a format for display
     * @param programStudy
     * @return Map - Key = string representing study UUID, value = formatted BrAPIStudy
     */
    private Map<String,BrAPIStudy> processStudyForDisplay(List<BrAPIStudy> programStudy, String programKey) {
        Map<String, BrAPIStudy> programStudyMap = new LinkedHashMap<>();
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
            String studyId = extRef.getReferenceId();
            programStudyMap.put(studyId, study);
        }

        return programStudyMap;
    }
}