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
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.genotype.SamplesApi;
import org.brapi.v2.model.geno.BrAPISample;
import org.brapi.v2.model.geno.request.BrAPISampleSearchRequest;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.utilities.BrAPIDAOUtil;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class BrAPISampleDAO {

    private final String referenceSource;

    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;

    @Inject
    public BrAPISampleDAO(ProgramDAO programDAO,
                          ImportDAO importDAO,
                          BrAPIDAOUtil brAPIDAOUtil,
                          BrAPIEndpointProvider brAPIEndpointProvider,
                          @Property(name = "brapi.server.reference-source") String referenceSource) {
        this.referenceSource = referenceSource;
        this.programDAO = programDAO;
        this.importDAO = importDAO;
        this.brAPIDAOUtil = brAPIDAOUtil;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
    }

    public List<BrAPISample> createSamples(Program program, List<BrAPISample> samplesToSave, ImportUpload upload) throws ApiException {
        SamplesApi samplesApi = brAPIEndpointProvider.get(programDAO.getSampleClient(program.getId()), SamplesApi.class);

        return brAPIDAOUtil.post(samplesToSave, upload, samplesApi::samplesPost, importDAO::update);
    }

    public List<BrAPISample> readSamplesByIds(Program program, List<String> sampleExternalIds) throws ApiException {
        if(sampleExternalIds.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPISampleSearchRequest searchRequest = new BrAPISampleSearchRequest().externalReferenceIDs(sampleExternalIds)
                                                                               .externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.SAMPLES)));

        SamplesApi samplesApi = brAPIEndpointProvider.get(programDAO.getSampleClient(program.getId()), SamplesApi.class);
        return brAPIDAOUtil.search(samplesApi::searchSamplesPost, samplesApi::searchSamplesSearchResultsDbIdGet, searchRequest);
    }

    public List<BrAPISample> readSamplesByPlateIds(Program program, List<String> plateExternalIds) throws ApiException {
        if(plateExternalIds.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPISampleSearchRequest searchRequest = new BrAPISampleSearchRequest().externalReferenceIDs(plateExternalIds)
                                                                               .externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATES)));

        SamplesApi samplesApi = brAPIEndpointProvider.get(programDAO.getSampleClient(program.getId()), SamplesApi.class);
        return brAPIDAOUtil.search(samplesApi::searchSamplesPost, samplesApi::searchSamplesSearchResultsDbIdGet, searchRequest);
    }

    public List<BrAPISample> readSamplesBySubmissionIds(Program program, List<String> submissionExternalIds) throws ApiException {
        if(submissionExternalIds.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPISampleSearchRequest searchRequest = new BrAPISampleSearchRequest().externalReferenceIDs(submissionExternalIds)
                                                                               .externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PLATE_SUBMISSIONS)));

        SamplesApi samplesApi = brAPIEndpointProvider.get(programDAO.getSampleClient(program.getId()), SamplesApi.class);
        return brAPIDAOUtil.search(samplesApi::searchSamplesPost, samplesApi::searchSamplesSearchResultsDbIdGet, searchRequest);
    }
}
