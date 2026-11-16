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

package org.breedinginsight.utilities;

import io.micronaut.context.annotation.Property;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.reactivex.functions.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.model.queryParams.core.BrAPIQueryParams;
import org.brapi.v2.model.*;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.brapi.v2.model.BrAPIWSMIMEDataTypes.APPLICATION_JSON;

@Singleton
@Slf4j
public class BrAPIDAOUtil {

    private final int searchWaitTime;
    private final Duration searchTimeout;
    private final int pageSize;
    private final int postGroupSize;
    private final int brapiFetchPageSize;
    private final ProgramService programService;

    @Inject
    public BrAPIDAOUtil(@Property(name = "brapi.search.wait-time") int searchWaitTime,
                        @Property(name = "brapi.read-timeout") Duration searchTimeout,
                        @Property(name = "brapi.page-size") int pageSize,
                        @Property(name = "brapi.post-group-size") int postGroupSize,
                        @Property(name = "brapi.cache.fetch-page-size") int brapiFetchPageSize,
                        ProgramService programService) {
        this.searchWaitTime = searchWaitTime;
        this.searchTimeout = searchTimeout;
        this.pageSize = pageSize;
        this.postGroupSize = postGroupSize;
        this.brapiFetchPageSize = brapiFetchPageSize;
        this.programService = programService;
    }

    public <T extends BrAPIResponse<U>, U extends BrAPIResponseResult<V>, V, W extends BrAPIQueryParams> List<V> get(Function<W, ApiResponse<T>> getMethod,
                                                                       W queryParams) throws ApiException {
        T brapiResponseResult;

        boolean paginationSet = queryParams.page() != null && queryParams.pageSize() != null;

        if (!paginationSet) {
            queryParams.setPage(0);
            queryParams.setPageSize(pageSize);
        }

        try {
            brapiResponseResult = Optional.ofNullable(getMethod.apply(queryParams))
                    .map(ApiResponse::getBody)
                    .orElseThrow();
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw e;
        } catch (NoSuchElementException e) {
            log.debug("Unable to retrieve BrAPIResponse from POST search request", e);
            throw new InternalServerException(e.toString(), e);
        } catch (Exception e) {
            log.debug("error", e);
            throw new InternalServerException(e.toString(), e);
        }

        BrAPIPagination responsePagination = Optional.of(brapiResponseResult)
                .map(BrAPIResponse::getMetadata)
                .map(BrAPIMetadata::getPagination)
                .orElse(null);

        if (responsePagination == null) {
            throw new InternalServerException("Expected pagination metadata in BrAPI get response not present");
        }

        if (!paginationSet && responsePagination.getTotalPages() > 1) {
            // Size of BrAPI entity GET data can not exceed configured pageSize.
            // If it does, it is recommended the GET method not be used for this purpose.
            // If you are trying to utilize this method to get a large amount of data for an entity, you can use
            // searchInternalReturnList for now.  In the future, we need a more robust way of transmitting this data using
            // compression client techniques to reduce strain on the server and avoid keeping all the database data
            // in java memory.
            throw new InternalServerException(String.format("More than one page found, meaning there is more data to be returned " +
                    "with pageSize = [%s]", queryParams.pageSize()));
        }

        return getListResult(brapiResponseResult);
    }

    public <T extends BrAPIResponse<U>, U extends BrAPIResponseResult<V>, V, W extends BrAPISearchRequestParametersPaging> List<V> search(Function<W, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                               Function3<String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                               W searchBody
    ) throws ApiException {
        return searchInternalReturnList(searchMethod, searchGetMethod, null, searchBody, true);
    }

    public <T extends BrAPIResponse<U>, U extends BrAPIResponseResult<V>, V, W extends BrAPISearchRequestParametersPaging> List<V> search(Function<W, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                               Function4<BrAPIWSMIMEDataTypes, String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMimedGetMethod,
                                                                               W searchBody
    ) throws ApiException {
        return searchInternalReturnList(searchMethod, null, searchMimedGetMethod, searchBody, true);
    }

    // Sends a boolean to prevent paging through the entire response to retrieve all data.  Necessary in cases where specific paging is required.
    public <T extends BrAPIResponse<U>, U extends BrAPIResponseResult<V>, V, W extends BrAPISearchRequestParametersPaging> List<V> searchNoPageThru(Function<W, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                                                               Function3<String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                                                               W searchBody
    ) throws ApiException {
        return searchInternalReturnList(searchMethod, searchGetMethod, null, searchBody, false);
    }

    private <T extends BrAPIResponse<U>, U extends BrAPIResponseResult<V>, V, W extends BrAPISearchRequestParametersPaging> List<V> searchInternalReturnList(Function<W, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                                                                                                             Function3<String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                                                                                                             Function4<BrAPIWSMIMEDataTypes, String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethodWithMimeType,
                                                                                                                                                             W searchBody, boolean pageThru) throws ApiException {
        return getListResult(searchInternalReturnResponse(searchMethod, searchGetMethod, searchGetMethodWithMimeType, searchBody, pageThru));
    }

    public <T extends BrAPIResponse<U>, U extends BrAPIResponseResult<V>, V, W extends BrAPISearchRequestParametersPaging> T searchReturnResponse(Function<W, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                                                                                                  Function3<String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                                                                                                  W searchBody
    ) throws ApiException {

        boolean pageThru = (searchBody.getPage() == null || searchBody.getPage() == 0) && (searchBody.getPageSize() == null || searchBody.getPageSize() == 0);

        return searchInternalReturnResponse(searchMethod, searchGetMethod, null, searchBody, pageThru);
    }

    public <T extends BrAPIResponse<U>, U extends BrAPIResponseResult<V>, V, W extends BrAPISearchRequestParametersPaging> T searchReturnResponse(Function<W, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                                                                                                  Function4<BrAPIWSMIMEDataTypes, String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMimedGetMethod,
                                                                                                                                                  W searchBody
    ) throws ApiException {
        boolean pageThru = (searchBody.getPage() == null || searchBody.getPage() == 0) && (searchBody.getPageSize() == null || searchBody.getPageSize() == 0);

        return searchInternalReturnResponse(searchMethod, null, searchMimedGetMethod, searchBody, pageThru);
    }

    /**
     * @param searchMethod The BrAPI API search post method, e.g. ObservationUnitsApi.searchObservationsPost()
     *
     * For the following two params, it should be noted some BrAPI APIs only support one of the following GET methods (e.g. ObservationUnitsApi)  In those cases, BI passes in the existing GET method.
     * @param searchGetMethod The BrAPI API search GET by searchDbId method.  This is used when async search is triggered by > 5 params in BrAPI search method.
     *                        The searchDbId is returned by the original postMethod if it is triggered. e.g. ObservationsApi.searchObservationsSearchResultsDbIdGet()
     * @param searchGetMethodWithMimeType The BrAPI API search GET method that triggers an async response. This is used when async search is triggered by > 5 params in BrAPI search method, and no searchGetMethod exists.
     *                                    The searchDbId is returned by the original postMethod if it is triggered. e.g. ObservationUnitsApi.searchObservationunitsSearchResultsDbIdGet.
     *
     * @param searchBody The BrAPI entity search request being supplied for this search
     * @param pageThru Determines whether all existing data related to the request should be retrieved, ignoring pagination.
     * For this method and all callers above, the parameters are described as follows:
     * @param <T> A BrAPIResponse object
     * @param <U> A BrAPIResponseResult object
     * @param <V> The BrAPI model object contained in list returned by U.getData()
     * @param <W> The specific BrAPI search request type
     * An example in action, say GermplasmApi.searchGermplasmPost() is supplied as the search method. This returns a BrAPIGermplasmListResponse class, which is T.
     * The U then, is BrAPIGermplasmListResponseResult, and V is BrAPIGermplasm.
     * In this example, W should be a BrAPIGermplasmSearchRequest
     * @return The full BrAPIResponse object from a completed BrAPI search request
     */
    private <T extends BrAPIResponse<U>, U extends BrAPIResponseResult<V>, V, W extends BrAPISearchRequestParametersPaging> T searchInternalReturnResponse(Function<W, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                                                                                                           Function3<String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                                                                                                           Function4<BrAPIWSMIMEDataTypes, String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethodWithMimeType,
                                                                                                                                                           W searchBody, boolean pageThru) throws ApiException {
        if (searchBody.getPageSize() != null && searchBody.getPageSize() > brapiFetchPageSize) {
            // Currently support for pages over 65k is not supported, which at the time of writing is the configured size of brapiFetchPageSize, which in .env is paging.page-size.max-allowed
            throw new ApiException(HttpStatus.BAD_REQUEST.getCode(), String.format("Maximum allowable pageSize for a BrAPI request is [%s].  If more data than this is required, send request without specifying page or page size to retrieve all of it.",  brapiFetchPageSize));
        }

        try {
            T listResponse = null;

            if (pageThru) {
                // This should be set to whatever the maximum allowable value is configured in the brapi test server,
                // perhaps it should be configurable on bi side as well.
                // For reference, that prop name is paging.page-size.max-allowed
                searchBody.pageSize(brapiFetchPageSize);
            }

            ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> response = searchMethod.apply(searchBody);
            if (response.getBody().getLeft().isPresent()) {
                listResponse = response.getBody().getLeft().get();

            /*  NOTE: may want to check for additional pages depending on whether BrAPI standard specifies how
                pagination params are handled for POST search endpoints or the corresponding endpoints in Breedbase are
                changed or updated
             */
                if (pageThru && hasMorePages(listResponse)) {
                    int currentPage = listResponse.getMetadata().getPagination().getCurrentPage() + 1;
                    int totalPages = listResponse.getMetadata().getPagination().getTotalPages();

                    while (currentPage < totalPages) {
                        searchBody.setPage(currentPage);
                        response = searchMethod.apply(searchBody);
                        if (response.getBody().getLeft().isPresent()) {
                            listResponse.getResult().getData().addAll(getListResult(response));
                        }

                        currentPage++;
                    }
                }
            } else {
                // Hit the get endpoint until we get a response
                Integer accruedWait = 0;
                Boolean searchFinished = false;
                int currentPage = 0;
                while (!searchFinished) {
                    BrAPIAcceptedSearchResponse searchResult = response.getBody().getRight().get();

                    ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> searchGetResponse =
                            searchGetResponse(searchGetMethod, searchGetMethodWithMimeType, searchResult, currentPage);
                    if (searchGetResponse.getBody().getLeft().isPresent()) {
                        searchFinished = true;
                        listResponse = searchGetResponse.getBody().getLeft().get();

                        if(pageThru && hasMorePages(listResponse)) {
                            currentPage++;
                            int totalPages = listResponse.getMetadata()
                                    .getPagination()
                                    .getTotalPages();

                            while (currentPage < totalPages) {
                                searchGetResponse = searchGetResponse(searchGetMethod, searchGetMethodWithMimeType, searchResult, currentPage);
                                if (searchGetResponse.getBody().getLeft().isPresent()) {
                                    listResponse.getResult().getData().addAll(getListResult(searchGetResponse));
                                }

                                currentPage++;
                            }
                        }
                    } else {
                        // Wait a bit before we call again
                        Thread.sleep(searchWaitTime);
                        accruedWait += searchWaitTime;
                        if (accruedWait >= searchTimeout.toMillis()) {
                            throw new ApiException("Search response timeout");
                        }
                    }
                }
            }

            if (pageThru) {
                BrAPIPagination pagination = listResponse.getMetadata().getPagination();

                // If pageThru is true, all the data found with the search request was retrieved, rendering paging info useless.
                // To accommodate for this, re-assign some of the metadata params.
                // TODO: In this use case, align bi/brapi-server/client to return all nulls.
                pagination.setPageSize(pagination.getTotalCount());
                pagination.setTotalPages(1);
                pagination.setCurrentPage(0);

                listResponse.getMetadata().setPagination(pagination);
            }

            return listResponse;
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw e;
        } catch (Exception e) {
            log.debug("error", e);
            throw new InternalServerException(e.toString(), e);
        }
    }

    public <T, U extends BrAPISearchRequestParametersTokenPaging, V> List<V> searchWithToken(Function<U, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                               Function3<String, String, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                               U searchBody
    ) throws ApiException {
        try {
            List<V> listResult = new ArrayList<>();
            //NOTE: Because of the way Breedbase implements BrAPI searches, the page size is initially set to an
            //arbitrary, large value to ensure that in the event that a 202 response is returned, the searchDbId
            //stored will refer to all records of the BrAPI variable.
            searchBody.pageSize(pageSize);
            ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> response = searchMethod.apply(searchBody);
            if (response.getBody().getLeft().isPresent()) {
                BrAPIResponse listResponse = (BrAPIResponse) response.getBody().getLeft().get();
                listResult = getListResult(response);

            /*  NOTE: may want to check for additional pages depending on whether BrAPI standard specifies how
                pagination params are handled for POST search endpoints or the corresponding endpoints in Breedbase are
                changed or updated
            */
                if(listResponse.getMetadata().getPagination() instanceof BrAPITokenPagination) {
                    String nextPageToken = ((BrAPITokenPagination) listResponse.getMetadata()
                                                                               .getPagination()).getNextPageToken();
                    while (StringUtils.isNotBlank(nextPageToken)) {
                        searchBody.setPageToken(nextPageToken);
                        response = searchMethod.apply(searchBody);
                        if (response.getBody()
                                    .getLeft()
                                    .isPresent()) {
                            listResult.addAll(getListResult(response));
                            listResponse = (BrAPIResponse) response.getBody().getLeft().get();
                            nextPageToken = ((BrAPITokenPagination) listResponse.getMetadata()
                                                                                .getPagination()).getNextPageToken();
                        } else {
                            nextPageToken = null;
                        }
                    }
                } else if(listResponse.getMetadata().getPagination() instanceof BrAPIIndexPagination) {
                    if(hasMorePages(listResponse)) {
                        int currentPage = listResponse.getMetadata().getPagination().getCurrentPage() + 1;
                        int totalPages = listResponse.getMetadata().getPagination().getTotalPages();

                        while (currentPage < totalPages) {
                            searchBody.setPage(currentPage);
                            response = searchMethod.apply(searchBody);
                            if (response.getBody().getLeft().isPresent()) {
                                listResult.addAll(getListResult(response));
                            }

                            currentPage++;
                        }
                    }
                }
            } else {
                // Hit the get endpoint until we get a response
                Integer accruedWait = 0;
                Boolean searchFinished = false;
                while (!searchFinished) {
                    BrAPIAcceptedSearchResponse searchResult = response.getBody().getRight().get();
                    String nextPageToken = ((BrAPITokenPagination) searchResult.getMetadata().getPagination()).getNextPageToken();

                    ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> searchGetResponse = searchGetMethod.apply(searchResult.getResult().getSearchResultsDbId(), nextPageToken, pageSize);
                    if (searchGetResponse.getBody().getLeft().isPresent()) {
                        searchFinished = true;
                        BrAPIResponse listResponse = (BrAPIResponse) searchGetResponse.getBody().getLeft().get();
                        listResult = getListResult(searchGetResponse);

                        nextPageToken = ((BrAPITokenPagination) listResponse.getMetadata().getPagination()).getNextPageToken();
                        while (StringUtils.isNotBlank(nextPageToken)) {
                            searchGetResponse = searchGetMethod.apply(searchResult.getResult().getSearchResultsDbId(), nextPageToken, pageSize);
                            if (searchGetResponse.getBody().getLeft().isPresent()) {
                                listResult.addAll(getListResult(searchGetResponse));
                                nextPageToken = ((BrAPITokenPagination) listResponse.getMetadata().getPagination()).getNextPageToken();
                            }
                        }
                    } else {
                        // Wait a bit before we call again
                        Thread.sleep(searchWaitTime);
                        accruedWait += searchWaitTime;
                        if (accruedWait >= searchTimeout.toMillis()) {
                            throw new ApiException("Search response timeout");
                        }
                    }
                }
            }

            return listResult;
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.toString(), e);
        }
    }

    private <T> ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> searchGetResponse(Function3<String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                                                        Function4<BrAPIWSMIMEDataTypes, String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethodWithMimeType,
                                                                                                        BrAPIAcceptedSearchResponse searchResult,
                                                                                                        int currentPage) throws Exception{
        return searchGetMethod != null ? searchGetMethod.apply(searchResult.getResult().getSearchResultsDbId(), currentPage, pageSize) :
                searchGetMethodWithMimeType.apply(APPLICATION_JSON, searchResult.getResult().getSearchResultsDbId(), currentPage, pageSize);
    }

    private boolean hasMorePages(BrAPIResponse listResponse) {
        return listResponse.getMetadata() != null
                && listResponse.getMetadata().getPagination() != null
                && listResponse.getMetadata().getPagination().getCurrentPage() < listResponse.getMetadata().getPagination().getTotalPages() - 1;
    }

    private <T, V> List<V> getListResult(ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> searchGetResponse) {
        BrAPIResponse listResponse = (BrAPIResponse) searchGetResponse.getBody().getLeft().get();
        BrAPIResponseResult responseResult = (BrAPIResponseResult) listResponse.getResult();
        return responseResult != null ? responseResult.getData() :
                new ArrayList<>();
    }

    public <T extends BrAPIResponse, V> List<V> getListResult(T response) {
        return Optional.ofNullable(response)
                .map(BrAPIResponse::getResult)
                .map(result -> (BrAPIResponseResult<V>) result)
                .map(BrAPIResponseResult::getData)
                .orElseThrow();
    }

    public <T, R> List<R> post(List<T> brapiObjects,
                            ImportUpload upload,
                            Function<List<T>, ApiResponse> postMethod,
                            Consumer<ImportUpload> progressUpdateMethod) throws ApiException {

        List<R> listResult = new ArrayList<>();
        try {
            // Make the POST calls in chunks so we don't overload the brapi server
            Integer currentRightBorder = 0;
            // Set our finished to our current value for different objects were posted before
            Integer finished = upload != null && upload.getProgress().getFinished() != null ?
                    Math.toIntExact(upload.getProgress().getFinished()) : 0;
            while (currentRightBorder < brapiObjects.size()) {
                List<T> postChunk = brapiObjects.size() > (currentRightBorder + postGroupSize) ?
                        brapiObjects.subList(currentRightBorder, currentRightBorder + postGroupSize) :
                        brapiObjects.subList(currentRightBorder, brapiObjects.size());
                // Update our progress in the db
                if (upload != null) {
                    upload.updateProgress(finished, postChunk.size());
                    progressUpdateMethod.accept(upload);
                }
                ApiResponse response = postMethod.apply(postChunk);
                if (response.getBody() == null) {
                    throw new ApiException("Response is missing body", response.getStatusCode(), response.getHeaders(), null);
                }
                BrAPIResponse body = (BrAPIResponse) response.getBody();
                if (body.getResult() == null) {
                    throw new ApiException("Response body is missing result", response.getStatusCode(), response.getHeaders(), response.getBody().toString());
                }
                BrAPIResponseResult result = (BrAPIResponseResult) body.getResult();
                if (result.getData() == null) {
                    throw new ApiException("Response result is missing data", response.getStatusCode(), response.getHeaders(), response.getBody().toString());
                }
                List<R> data = result.getData();
                // TODO: Maybe move this outside of the loop
                if (data.size() != postChunk.size()) {
                    throw new ApiException("Number of brapi objects returned does not equal number sent");
                }
                listResult.addAll(data);
                finished += data.size();
                currentRightBorder += postGroupSize;
            }

            if (upload != null) {
                // Set finished count, reset inProgress count to 0.
                upload.updateProgress(finished, 0);
                progressUpdateMethod.accept(upload);
            }

            return listResult;
        } catch (ApiException e) {
            log.warn(Utilities.generateApiExceptionLogMessage(e));
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.toString(), e);
        }
    }

    public <T> T put(String dbId,
                           T brapiObject,
                            BiFunction<String, T, ApiResponse> putMethod) throws ApiException {
        try {
                ApiResponse response = putMethod.apply(dbId, brapiObject);
                if (response.getBody() == null) {
                    throw new ApiException("Response is missing body", response.getStatusCode(), response.getHeaders(), null);
                }
                BrAPIResponse body = (BrAPIResponse) response.getBody();
                if (body.getResult() == null) {
                    throw new ApiException("Response body is missing result", response.getStatusCode(), response.getHeaders(), response.getBody().toString());
                }
                return (T) body.getResult();

        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e));
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.toString(), e);
        }
    }

    public <T> List<T> post(List<T> brapiObjects,
                                   Function<List<T>, ApiResponse> postMethod) throws ApiException {
        return post(brapiObjects, null, postMethod, null);
    }

    /**
     * TODO: replace with brapi client methods when available, will do timeout spec from config at that point
     * @param brapiRequest
     * @return
     * @throws ApiException
     */
    public String makeCallWithResponse(Request brapiRequest) throws ApiException {
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.MINUTES)
                .build();

        // autoclose Response
        try (Response response = client.newCall(brapiRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new ApiException("Request failed with status code: " + response.code());
            }
            return response.body().string();
        } catch (IOException e) {
            throw new ApiException(e);
        }
    }

    public HttpResponse<String> makeCall(Request brapiRequest) {
        // Create OkHttpClient with timeout
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.MINUTES)
                .build();

        try (Response brapiResponse = client.newCall(brapiRequest).execute()) {
            int statusCode = brapiResponse.code();

            if (!brapiResponse.isSuccessful()) {
                return HttpResponse.status(HttpStatus.valueOf(statusCode));
            }

            String responseBody = brapiResponse.body() != null ? brapiResponse.body().string() : "";
            return HttpResponse.status(HttpStatus.valueOf(statusCode), responseBody);

        } catch (IOException e) {
            log.error("Error calling BrAPI Service", e);
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error calling BrAPI Service");
        }
    }

    public String getProgramBrAPIBaseUrl(UUID programId) {
        ProgramBrAPIEndpoints programBrAPIEndpoints;
        try {
            programBrAPIEndpoints = programService.getBrapiEndpoints(programId);
        } catch (DoesNotExistException e) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Program does not exist");
        }

        if(programBrAPIEndpoints.getCoreUrl().isEmpty()) {
            log.error("Program: " + programId + " is missing BrAPI URL config");
            throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "");
        }
        var programBrAPIBaseUrl = programBrAPIEndpoints.getCoreUrl().get();
        programBrAPIBaseUrl = programBrAPIBaseUrl.endsWith("/") ? programBrAPIBaseUrl.substring(0, programBrAPIBaseUrl.length() - 1) : programBrAPIBaseUrl;
        return programBrAPIBaseUrl.endsWith(BrapiVersion.BRAPI_V2) ? programBrAPIBaseUrl : programBrAPIBaseUrl + BrapiVersion.BRAPI_V2;
    }

    private BrAPIFilterBy constructFilterBy(String filterOn, String value) {
        var filterBy = new BrAPIFilterBy();

        filterBy.setFilterOn(filterOn);
        filterBy.setValue(value);
        return filterBy;
    }

    private BrAPISortBy constructSortBy(String sortOn, String sortOrder) {
        var sortBy = new BrAPISortBy();
        sortBy.setSortedOn(sortOn);

        BrAPISortOrder sortOrderEnum = BrAPISortOrder.valueOf(sortOrder.toUpperCase());

        if (sortOrderEnum == null) {
            sortOrderEnum = BrAPISortOrder.ASC;
        }

        sortBy.setSortOrder(sortOrderEnum);

        return sortBy;
    }

    /**
     * Sets three generic search parameters for an outgoing BrAPI Search Query:
     * - Sorting
     * - Filtering
     * - Pagination
     */
    public <T extends BrAPISearchRequestParametersPaging, U extends BrapiQuery> void setGenericSearchParameters(T brapiSearchRequest, U biSearchQuery) {

        if (biSearchQuery == null) {
            // If the search query is not available, assume we want all data retrieved using the search request.
            // This is accomplished by setting the page and pageSize to null.
            brapiSearchRequest.setPage(null);
            brapiSearchRequest.setPageSize(null);
            return;
        }

        // Set SortBy
        List<BrAPISortBy> brAPISortBy = new ArrayList<>();

        Map<String, String> brapiColNameByBiColName = biSearchQuery.getBrAPIColumnNamesByBiColumnName();

        if (StringUtils.isNotBlank(biSearchQuery.getSortField()) && brapiColNameByBiColName.containsKey(biSearchQuery.getSortField())) {
            brAPISortBy.add(constructSortBy(brapiColNameByBiColName.get(biSearchQuery.getSortField()), biSearchQuery.getSortOrder().toString()));
        }

        if (!brAPISortBy.isEmpty()) {
            brapiSearchRequest.setSortBy(brAPISortBy);
        }

        // Set FilterBy
        List<BrAPIFilterBy> brAPIFilterBy = new ArrayList<>();

        Map<String, String> filterValuesByBrAPIColName = biSearchQuery.getFilterValuesByBrAPIColumnName();

        filterValuesByBrAPIColName.forEach((brapiColumnName, value) -> {
            if (StringUtils.isNotBlank(value)) {
                brAPIFilterBy.add(constructFilterBy(brapiColumnName, value));
            }
        });

        if (!brAPIFilterBy.isEmpty()) {
            brapiSearchRequest.setFilterBy(brAPIFilterBy);
        }

        // Set Pagination
        if (biSearchQuery.getPage() == null) {
            brapiSearchRequest.setPage(biSearchQuery.getDefaultPage());
        } else {
            brapiSearchRequest.setPage(biSearchQuery.getPage());
        }

        if (biSearchQuery.getPageSize() == null) {
            brapiSearchRequest.setPageSize(biSearchQuery.getDefaultPageSize());
        } else  {
            brapiSearchRequest.setPageSize(biSearchQuery.getPageSize());
        }
    }

    public String getBrAPIProgramDbId(UUID biProgramId) throws ApiException {
        return programService.getBrAPIProgramDbId(biProgramId);
    }
}
