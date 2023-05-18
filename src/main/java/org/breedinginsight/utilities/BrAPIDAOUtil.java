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
import io.micronaut.http.server.exceptions.InternalServerException;
import io.reactivex.functions.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.*;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.response.BrAPIGermplasmSingleResponse;
import org.breedinginsight.brapps.importer.model.ImportUpload;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.brapi.v2.model.BrAPIWSMIMEDataTypes.APPLICATION_JSON;

@Singleton
@Slf4j
public class BrAPIDAOUtil {

    private final int searchWaitTime;
    private final Duration searchTimeout;
    private final int pageSize;
    private final int postGroupSize;

    @Inject
    public BrAPIDAOUtil(@Property(name = "brapi.search.wait-time") int searchWaitTime,
                        @Property(name = "brapi.read-timeout") Duration searchTimeout,
                        @Property(name = "brapi.page-size") int pageSize,
                        @Property(name = "brapi.post-group-size") int postGroupSize) {
        this.searchWaitTime = searchWaitTime;
        this.searchTimeout = searchTimeout;
        this.pageSize = pageSize;
        this.postGroupSize = postGroupSize;
    }

    public <T, U extends BrAPISearchRequestParametersPaging, V> List<V> search(Function<U, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                               Function3<String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                               U searchBody
    ) throws ApiException {
        return searchInternal(searchMethod, searchGetMethod, null, searchBody);
    }

    public <T, U extends BrAPISearchRequestParametersPaging, V> List<V> search(Function<U, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                               Function4<BrAPIWSMIMEDataTypes, String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                               U searchBody
    ) throws ApiException {
        return searchInternal(searchMethod, null, searchGetMethod, searchBody);
    }

    private <T, U extends BrAPISearchRequestParametersPaging, V> List<V> searchInternal(Function<U, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
                                                                                        Function3<String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethod,
                                                                                        Function4<BrAPIWSMIMEDataTypes, String, Integer, Integer, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchGetMethodWithMimeType,
                                                                                U searchBody) throws ApiException {
        try {
            List<V> listResult = new ArrayList<>();
            //NOTE: Because of the way Breedbase implements BrAPI searches, the page size is initially set to an
            //arbitrary, large value to ensure that in the event that a 202 response is returned, the searchDbId
            //stored will refer to all records of the BrAPI variable.
            searchBody.pageSize(10000000);
            ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> response = searchMethod.apply(searchBody);
            if (response.getBody().getLeft().isPresent()) {
                BrAPIResponse listResponse = (BrAPIResponse) response.getBody().getLeft().get();
                listResult = getListResult(response);

            /*  NOTE: may want to check for additional pages depending on whether BrAPI standard specifies how
                pagination params are handled for POST search endpoints or the corresponding endpoints in Breedbase are
                changed or updated
             */
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
                        BrAPIResponse listResponse = (BrAPIResponse) searchGetResponse.getBody().getLeft().get();
                        listResult = getListResult(searchGetResponse);

                        if(hasMorePages(listResponse)) {
                            currentPage++;
                            int totalPages = listResponse.getMetadata()
                                    .getPagination()
                                    .getTotalPages();

                            while (currentPage < totalPages) {
                                searchGetResponse = searchGetResponse(searchGetMethod, searchGetMethodWithMimeType, searchResult, currentPage);
                                if (searchGetResponse.getBody().getLeft().isPresent()) {
                                    listResult.addAll(getListResult(searchGetResponse));
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

            return listResult;
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

    // TODO: write generic put code
    public <T> List<T> put(List<T> brapiObjects,
                           ImportUpload upload,
                           Function<List<T>, ApiResponse> putMethod,
                           Consumer<ImportUpload> progressUpdateMethod) throws ApiException {
        throw new UnsupportedOperationException();
    }

    public <T> List<T> post(List<T> brapiObjects,
                                   ImportUpload upload,
                                   Function<List<T>, ApiResponse> postMethod,
                                   Consumer<ImportUpload> progressUpdateMethod) throws ApiException {

        List<T> listResult = new ArrayList<>();
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
                List<T> data = result.getData();
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

}
