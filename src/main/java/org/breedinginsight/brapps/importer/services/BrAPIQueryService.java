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

package org.breedinginsight.brapps.importer.services;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import okhttp3.Call;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.formula.functions.T;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.ProgramQueryParams;
import org.brapi.client.v2.modules.core.LocationsApi;
import org.brapi.client.v2.modules.core.ProgramsApi;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.client.v2.modules.germplasm.GermplasmAttributesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.brapi.v2.model.BrAPIResponse;
import org.brapi.v2.model.BrAPIResponseResult;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.BrAPIProgram;
import org.brapi.v2.model.core.request.BrAPILocationSearchRequest;
import org.brapi.v2.model.core.response.BrAPILocationListResponse;
import org.brapi.v2.model.core.response.BrAPIProgramListResponse;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.BrAPIGermplasmAttribute;
import org.brapi.v2.model.germ.request.BrAPIGermplasmAttributeSearchRequest;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.brapi.v2.model.germ.response.BrAPIGermplasmAttributeListResponse;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponseResult;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.request.BrAPIObservationUnitSearchRequest;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.breedinginsight.brapps.importer.model.base.Germplasm;
import org.breedinginsight.brapps.importer.model.base.ObservationUnit;
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Singleton
public class BrAPIQueryService {

    private BrAPIProvider brAPIProvider;
    private static Integer SEARCH_WAIT_TIME = 1000;
    private static Integer SEARCH_TIMEOUT = Long.valueOf(TimeUnit.MINUTES.toMillis(10)).intValue();
    private static Integer RESULTS_PER_QUERY = 100000;
    public static String OU_ID_REFERENCE_SOURCE = "ou_id";
    public static Integer POST_GROUP_SIZE = 1000;

    @Property(name = "brapi.server.reference-source")
    private String referenceSource;

    @Inject
    public BrAPIQueryService(BrAPIProvider brAPIProvider) {
        this.brAPIProvider = brAPIProvider;
    }

    public <T, U, V> List<V> search(Function<U, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                               Function3<String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                               U searchBody
    ) throws ApiException {

        try {
            List<V> listResult = new ArrayList<>();
            ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> response =
                    searchMethod.apply(searchBody);
            if (response.getBody().getLeft().isPresent()) {
                BrAPIResponse listResponse = (BrAPIResponse) response.getBody().getLeft().get();
                BrAPIResponseResult responseResult = (BrAPIResponseResult) listResponse.getResult();
                listResult = responseResult != null ? responseResult.getData() :
                        new ArrayList<>();
                //TODO: Check that all of the pages were returned
            } else {
                // Hit the get endpoint until we get a response
                Integer accruedWait = 0;
                ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> searchGetResponse = null;
                while (searchGetResponse == null) {
                    BrAPIAcceptedSearchResponse searchResult = response.getBody().getRight().get();

                    // TODO: Check if we have more to get for pages
                    searchGetResponse = searchGetMethod.apply(searchResult.getResult().getSearchResultsDbId(), 0, RESULTS_PER_QUERY);
                    if (searchGetResponse.getBody().getLeft().isPresent()) {
                        BrAPIResponse listResponse = (BrAPIResponse) searchGetResponse.getBody().getLeft().get();
                        BrAPIResponseResult responseResult = (BrAPIResponseResult) listResponse.getResult();
                        listResult = responseResult != null ? responseResult.getData() :
                                new ArrayList<>();
                    } else {
                        // Wait a bit before we call again
                        Thread.sleep(SEARCH_WAIT_TIME);
                        accruedWait += SEARCH_WAIT_TIME;
                        if (accruedWait >= SEARCH_TIMEOUT) {
                            throw new ApiException("Search response timeout");
                        }
                    }
                }
            }

            return listResult;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.toString(), e);
        }
    }

    private <T> List<T> post(List<T> brapiObjects, Function<List<T>, ApiResponse> postMethod) throws ApiException {

        List<T> listResult = new ArrayList<>();
        try {
            // Make the POST calls in chunks so we don't overload the brapi server
            Integer currentRightBorder = 0;
            while (currentRightBorder < brapiObjects.size()) {
                List<T> postChunk = brapiObjects.size() > (currentRightBorder + POST_GROUP_SIZE - 1) ?
                        brapiObjects.subList(currentRightBorder, currentRightBorder + POST_GROUP_SIZE - 1) : brapiObjects.subList(currentRightBorder, brapiObjects.size() - 1);
                ApiResponse response = postMethod.apply(postChunk);
                if (response.getBody() == null) throw new ApiException("Response is missing body");
                BrAPIResponse body = (BrAPIResponse) response.getBody();
                if (body.getResult() == null) throw new ApiException("Response body is missing result");
                BrAPIResponseResult result = (BrAPIResponseResult) body.getResult();
                if (result.getData() == null) throw new ApiException("Response result is missing data");
                List<T> data = result.getData();
                // TODO: Maybe move this outside of the loop
                if (data.size() != postChunk.size()) throw new ApiException("Number of brapi objects returned does not equal number sent");
                listResult.addAll(data);
                currentRightBorder += POST_GROUP_SIZE;
            }

            return listResult;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.toString(), e);
        }
    }

    public List<BrAPIGermplasm> getGermplasmByName(List<String> germplasmNames, BrAPIProgram brAPIProgram) throws ApiException {
        BrAPIGermplasmSearchRequest germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.germplasmNames(germplasmNames);
        // TODO: Should be have a 'Pedigree' study to connect the germplasm to the program? Makes it a little less flexible
        // Germplasm doesn't have program attached. Do species as next best thing
        germplasmSearch.setCommonCropNames(List.of(brAPIProgram.getCommonCropName()));
        GermplasmApi api = brAPIProvider.getGermplasmApi(BrAPIClientType.CORE);
        return this.search(
                api::searchGermplasmPost,
                api::searchGermplasmSearchResultsDbIdGet,
                germplasmSearch
        );
    }

    public List<BrAPIGermplasmAttribute> getGermplasmAttributesByName(List<String> germplasmAttributeNames, BrAPIProgram program) throws ApiException {

        BrAPIGermplasmAttributeSearchRequest germplasmAttributeSearch = new BrAPIGermplasmAttributeSearchRequest();
        germplasmAttributeSearch.setAttributeNames(new ArrayList<>(germplasmAttributeNames));
        GermplasmAttributesApi api = brAPIProvider.getGermplasmAttributesApi(BrAPIClientType.CORE);
        return this.search(
                api::searchAttributesPost,
                api::searchAttributesSearchResultsDbIdGet,
                germplasmAttributeSearch
        );
    }

    public List<BrAPIObservationUnit> getObservationUnitsByNameAndStudyName(List<Pair<String, String>> nameStudyPairs, BrAPIProgram brAPIProgram) throws ApiException {

        List<String> observationUnitNames = nameStudyPairs.stream().map(Pair::getLeft).collect(Collectors.toList());
        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.setObservationUnitNames(new ArrayList<>(observationUnitNames));
        observationUnitSearchRequest.setProgramDbIds(List.of(brAPIProgram.getProgramDbId()));
        ObservationUnitsApi api = brAPIProvider.getObservationUnitApi(BrAPIClientType.CORE);
        List<BrAPIObservationUnit> observationUnits = this.search(
                api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                observationUnitSearchRequest
        );

        // TODO: Select for study as well
        return observationUnits;
    }

    public List<BrAPILocation> getLocationsByName(List<String> locationNames, BrAPIProgram program) throws ApiException {

        BrAPILocationSearchRequest locationSearchRequest = new BrAPILocationSearchRequest();
        locationSearchRequest.setLocationNames(new ArrayList<>(locationNames));
        //TODO: Locations don't connect to programs. How to get locations for the program?
        LocationsApi api = brAPIProvider.getLocationsApi(BrAPIClientType.CORE);
        return this.search(
                api::searchLocationsPost,
                api::searchLocationsSearchResultsDbIdGet,
                locationSearchRequest
        );
    }

    public Optional<BrAPIProgram> getProgram(UUID programId) throws ApiException {
        ProgramsApi programsApi = brAPIProvider.getProgramsAPI(BrAPIClientType.CORE);
        ProgramQueryParams params = new ProgramQueryParams();
        params.externalReferenceID(programId.toString());
        params.externalReferenceSource(referenceSource);
        ApiResponse<BrAPIProgramListResponse> programsResponse = programsApi.programsGet(params);
        if (programsResponse.getBody() == null ||
                programsResponse.getBody().getResult() == null ||
                programsResponse.getBody().getResult().getData() == null
        ) {
            throw new ApiException("Query response was not properly structure.");
        }

        List<BrAPIProgram> programs = programsResponse.getBody().getResult().getData();
        if (programs.size() == 1) {
            return Optional.of(programs.get(0));
        } else {
            return Optional.empty();
        }
    }

    public List<BrAPIGermplasm> createBrAPIGermplasm(List<BrAPIGermplasm> brAPIGermplasmList) throws ApiException {
        GermplasmApi api = brAPIProvider.getGermplasmApi(BrAPIClientType.CORE);
        return this.post(brAPIGermplasmList, api::germplasmPost);
    }

    public List<BrAPIObservationUnit> createBrAPIObservationUnits(List<BrAPIObservationUnit> brAPIObservationUnitList) throws ApiException {
        ObservationUnitsApi api = brAPIProvider.getObservationUnitApi(BrAPIClientType.CORE);
        return this.post(brAPIObservationUnitList, api::observationunitsPost);
    }


}
