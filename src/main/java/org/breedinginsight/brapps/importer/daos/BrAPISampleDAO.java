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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.brapi.client.v2.JSON;
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

@Slf4j
@Singleton
public class BrAPISampleDAO {

    private final String referenceSource;

    private final ProgramDAO programDAO;
    private final ImportDAO importDAO;
    private final BrAPIDAOUtil brAPIDAOUtil;
    private final BrAPIEndpointProvider brAPIEndpointProvider;
    private final Gson gson = new JSON().getGson();

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
            return  Collections.emptyList();
        }

        BrAPISampleSearchRequest searchRequest = new BrAPISampleSearchRequest().externalReferenceIDs(sampleExternalIds)
                                                                               .externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.SAMPLES)));

        SamplesApi samplesApi = brAPIEndpointProvider.get(programDAO.getSampleClient(program.getId()), SamplesApi.class);
        return brAPIDAOUtil.search(samplesApi::searchSamplesPost, samplesApi::searchSamplesSearchResultsDbIdGet, searchRequest);
    }

    public List<BrAPISample> readSamplesByGermplasmIds(Program program, List<String> germplasmExternalIds) throws ApiException {
        if(germplasmExternalIds.isEmpty()) {
            return Collections.emptyList();
        }

        BrAPISampleSearchRequest searchRequest = new BrAPISampleSearchRequest().externalReferenceIDs(germplasmExternalIds)
                .externalReferenceSources(List.of(Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.GERMPLASM)));

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

    /**
     * Deletes all samples specified in the brapi server
     * @param program
     * @param sampleDbIds
     * @throws ApiException
     */
    public void deleteSamples(Program program, List<String> sampleDbIds) throws ApiException {
        // create batch of samples, not yet included in brapi client TODO: switch to brapi client when available
        String programBrAPIBaseUrl = brAPIDAOUtil.getProgramBrAPIBaseUrl(program.getId());
        String batchDbId = postSamplesBatch(programBrAPIBaseUrl, sampleDbIds);

        // delete samples specified in batch
        deleteBatch(programBrAPIBaseUrl, batchDbId);
    }

    /**
     * Deletes all plates specified in the brapi server
     * @param program
     * @param plateDbIds
     * @throws ApiException
     */
    public void deletePlates(Program program, List<String> plateDbIds) throws ApiException {
        // create batch of plates, not yet included in brapi client TODO: switch to brapi client when available
        String programBrAPIBaseUrl = brAPIDAOUtil.getProgramBrAPIBaseUrl(program.getId());
        String batchDbId = postPlatesBatch(programBrAPIBaseUrl, plateDbIds);

        // delete plates specified in batch
        deleteBatch(programBrAPIBaseUrl, batchDbId);
    }


    private String postSamplesBatch(String programBrAPIBaseUrl, List<String> sampleDbIds) throws ApiException {
        HttpUrl.Builder requestUrl = HttpUrl.parse(programBrAPIBaseUrl + "/batchDeletes").newBuilder();
        SampleBatchDeleteRequest requestBody = new SampleBatchDeleteRequest(sampleDbIds);
        String json = gson.toJson(requestBody);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        HttpUrl url = requestUrl.build();
        return postBatch(url, body, programBrAPIBaseUrl);
    }

    private String postPlatesBatch(String programBrAPIBaseUrl, List<String> plateDbIds) throws ApiException {
        HttpUrl.Builder requestUrl = HttpUrl.parse(programBrAPIBaseUrl + "/batchDeletes").newBuilder();
        PlateBatchDeleteRequest requestBody = new PlateBatchDeleteRequest(plateDbIds);
        String json = gson.toJson(requestBody);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        HttpUrl url = requestUrl.build();
        return postBatch(url, body, programBrAPIBaseUrl);
    }

    private String postBatch(HttpUrl url, RequestBody body, String programBrAPIBaseUrl) throws ApiException {

        Request brapiRequest = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        String jsonResponse = brAPIDAOUtil.makeCallWithResponse(brapiRequest);
        JsonElement rootElement = JsonParser.parseString(jsonResponse);
        JsonObject rootObject = rootElement.getAsJsonObject();
        JsonObject resultObject = rootObject.getAsJsonObject("result");

        // check to see if immediate response or searchResultId
        if(resultObject.has("batchDeleteDbId")) {
            return resultObject.get("batchDeleteDbId").getAsString();
        } else if (resultObject.has("searchResultsDbId")) {
            // TODO: once api stuff is in client use BrAPIDAOUtil::search to handle retries, for now just request once
            // brapi server only returns immediate response for batchDeletes so this case won't happen
            return getBatchDeleteDbIdFromSearchResult(programBrAPIBaseUrl, resultObject.get("searchResultsDbId").getAsString());
        } else {
            throw new InternalServerException("Expected batchDeleteDbId or searchResultsDbId but got " + resultObject);
        }
    }

    private String getBatchDeleteDbIdFromSearchResult(String programBrAPIBaseUrl, String searchResultDbId) throws ApiException {
        HttpUrl.Builder requestUrl = HttpUrl.parse(programBrAPIBaseUrl + "/search/batchDeletes/" + searchResultDbId).newBuilder();

        HttpUrl url = requestUrl.build();
        Request brapiRequest = new Request.Builder()
                .url(url)
                .method("GET", null)
                .addHeader("Content-Type", "application/json")
                .build();

        String jsonResponse = brAPIDAOUtil.makeCallWithResponse(brapiRequest);
        JsonElement rootElement = JsonParser.parseString(jsonResponse);
        JsonObject rootObject = rootElement.getAsJsonObject();
        JsonObject resultObject = rootObject.getAsJsonObject("result");
        return resultObject.get("batchDeleteDbId").getAsString();
    }

    private void deleteBatch(String programBrAPIBaseUrl, String batchDbId) throws ApiException {
        HttpUrl.Builder requestUrl = HttpUrl.parse(programBrAPIBaseUrl + "/batchDeletes/" + batchDbId).newBuilder();
        requestUrl.addQueryParameter("hardDelete", "true");

        HttpUrl url = requestUrl.build();
        Request brapiRequest = new Request.Builder()
                .url(url)
                .method("DELETE", null)
                .addHeader("Content-Type", "application/json")
                .build();

        brAPIDAOUtil.makeCall(brapiRequest);
    }

    /**
     * TODO: temporary minimal model here until brapi client is updated with delete models
     */
    public class SampleBatchDeleteRequest {
        private String batchDeleteType;
        private Search search;

        public SampleBatchDeleteRequest(List<String> sampleDbIds) {
            this.batchDeleteType = "samples";
            this.search = new Search(sampleDbIds);
        }

        private class Search {
            private List<String> sampleDbIds;

            public Search(List<String> sampleDbIds) {
                this.sampleDbIds = sampleDbIds;
            }
        }
    }

    public class PlateBatchDeleteRequest {
        private String batchDeleteType;
        private Search search;

        public PlateBatchDeleteRequest(List<String> plateDbIds) {
            this.batchDeleteType = "plates";
            this.search = new Search(plateDbIds);
        }

        private class Search {
            private List<String> plateDbIds;

            public Search(List<String> plateDbIds) {
                this.plateDbIds = plateDbIds;
            }
        }
    }

}
