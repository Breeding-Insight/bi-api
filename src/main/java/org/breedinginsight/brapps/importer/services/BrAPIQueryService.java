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

import io.micronaut.http.server.exceptions.InternalServerException;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import okhttp3.Call;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.poi.ss.formula.functions.T;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.core.LocationsApi;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.client.v2.modules.germplasm.GermplasmAttributesApi;
import org.brapi.client.v2.modules.phenotype.ObservationUnitsApi;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.brapi.v2.model.BrAPIResponse;
import org.brapi.v2.model.BrAPIResponseResult;
import org.brapi.v2.model.core.BrAPILocation;
import org.brapi.v2.model.core.request.BrAPILocationSearchRequest;
import org.brapi.v2.model.core.response.BrAPILocationListResponse;
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
import org.breedinginsight.services.brapi.BrAPIClientType;
import org.breedinginsight.services.brapi.BrAPIProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Singleton
public class BrAPIQueryService {

    private BrAPIProvider brAPIProvider;
    private static Integer SEARCH_WAIT_TIME = 1000;
    private static Integer SEARCH_TIMEOUT = Long.valueOf(TimeUnit.MINUTES.toMillis(10)).intValue();
    private static Integer RESULTS_PER_QUERY = 10000;
    public static String OU_ID_REFERENCE_SOURCE = "ou_id";

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

    public List<BrAPIGermplasm> getGermplasmByName(List<String> germplasmNames) throws ApiException {
        BrAPIGermplasmSearchRequest germplasmSearch = new BrAPIGermplasmSearchRequest();
        germplasmSearch.germplasmNames(germplasmNames);
        GermplasmApi api = brAPIProvider.getGermplasmApi(BrAPIClientType.CORE);
        return this.<BrAPIGermplasmListResponse, BrAPIGermplasmSearchRequest, BrAPIGermplasm>search(
                api::searchGermplasmPost,
                api::searchGermplasmSearchResultsDbIdGet,
                germplasmSearch
        );
    }

    public List<BrAPIGermplasmAttribute> getGermplasmAttributesByName(List<String> germplasmAttributeNames) throws ApiException {

        BrAPIGermplasmAttributeSearchRequest germplasmAttributeSearch = new BrAPIGermplasmAttributeSearchRequest();
        germplasmAttributeSearch.setAttributeNames(new ArrayList<>(germplasmAttributeNames));
        GermplasmAttributesApi api = brAPIProvider.getGermplasmAttributesApi(BrAPIClientType.CORE);
        return this.<BrAPIGermplasmAttributeListResponse, BrAPIGermplasmAttributeSearchRequest, BrAPIGermplasmAttribute>search(
                api::searchAttributesPost,
                api::searchAttributesSearchResultsDbIdGet,
                germplasmAttributeSearch
        );
    }

    public List<BrAPIObservationUnit> getObservationUnitsByNameAndStudyName(List<Pair<String, String>> nameStudyPairs) throws ApiException {

        List<String> observationUnitNames = nameStudyPairs.stream().map(Pair::getLeft).collect(Collectors.toList());
        BrAPIObservationUnitSearchRequest observationUnitSearchRequest = new BrAPIObservationUnitSearchRequest();
        observationUnitSearchRequest.setObservationUnitNames(new ArrayList<>(observationUnitNames));
        ObservationUnitsApi api = brAPIProvider.getObservationUnitApi(BrAPIClientType.CORE);
        List<BrAPIObservationUnit> observationUnits = this.<BrAPIObservationUnitListResponse, BrAPIObservationUnitSearchRequest, BrAPIObservationUnit>search(
                api::searchObservationunitsPost,
                api::searchObservationunitsSearchResultsDbIdGet,
                observationUnitSearchRequest
        );

        // TODO: Select for study as well
        return observationUnits;
    }

    public List<BrAPILocation> getLocationsByName(List<String> locationNames) throws ApiException {

        BrAPILocationSearchRequest locationSearchRequest = new BrAPILocationSearchRequest();
        locationSearchRequest.setLocationNames(new ArrayList<>(locationNames));
        LocationsApi api = brAPIProvider.getLocationsApi(BrAPIClientType.CORE);
        return this.<BrAPILocationListResponse, BrAPILocationSearchRequest, BrAPILocation>search(
                api::searchLocationsPost,
                api::searchLocationsSearchResultsDbIdGet,
                locationSearchRequest
        );
    }
}
