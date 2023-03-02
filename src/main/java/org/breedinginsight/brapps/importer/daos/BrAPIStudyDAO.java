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

import io.micronaut.context.annotation.Property;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.StudiesApi;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.request.BrAPIStudySearchRequest;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Singleton
public class BrAPIStudyDAO {
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    private ProgramDAO programDAO;
    private ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPIStudyDAO(ProgramDAO programDAO, ImportDAO importDAO, BrAPIDAOUtil brAPIDAOUtil, BrAPIEndpointProvider brAPIEndpointProvider) {
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public Optional<BrAPIStudy> getStudyByName(String studyName, Program program) throws ApiException {
        List<BrAPIStudy> studies = getStudiesByName(List.of(studyName), program);
        return Utilities.getSingleOptional(studies);
    }
    public List<BrAPIStudy> getStudiesByName(List<String> studyNames, Program program) throws ApiException {
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

    public List<BrAPIStudy> getStudiesByExperimentID(UUID experimentID, Program program ) throws ApiException {
        BrAPIStudySearchRequest studySearch = new BrAPIStudySearchRequest();
        studySearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        studySearch.addExternalReferenceIDsItem(experimentID.toString());
        studySearch.addExternalReferenceSourcesItem(Utilities.generateReferenceSource(BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.TRIALS));
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(program.getId()), StudiesApi.class);
        return brAPIDAOUtil.search(
                api::searchStudiesPost,
                api::searchStudiesSearchResultsDbIdGet,
                studySearch
        );
    }

    public List<BrAPIStudy> createBrAPIStudies(List<BrAPIStudy> brAPIStudyList, UUID programId, ImportUpload upload) throws ApiException {
        StudiesApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), StudiesApi.class);
        return brAPIDAOUtil.post(brAPIStudyList, upload, api::studiesPost, importDAO::update);
    }

    public List<BrAPIStudy> getStudiesByStudyDbId(Collection<String> studyDbIds, Program program) throws ApiException {
        BrAPIStudySearchRequest studySearch = new BrAPIStudySearchRequest();
        studySearch.programDbIds(List.of(program.getBrapiProgram().getProgramDbId()));
        studySearch.studyDbIds(new ArrayList<>(studyDbIds));
        StudiesApi api = new StudiesApi(programDAO.getCoreClient(program.getId()));
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
}