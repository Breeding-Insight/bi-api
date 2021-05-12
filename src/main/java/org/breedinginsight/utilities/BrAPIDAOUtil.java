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

import io.micronaut.http.server.exceptions.InternalServerException;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;
import org.brapi.v2.model.BrAPIResponse;
import org.brapi.v2.model.BrAPIResponseResult;
import org.breedinginsight.brapps.importer.model.ImportUpload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class BrAPIDAOUtil {

    public static Integer SEARCH_WAIT_TIME = 1000;
    public static Integer SEARCH_TIMEOUT = Long.valueOf(TimeUnit.MINUTES.toMillis(10)).intValue();
    public static Integer RESULTS_PER_QUERY = 100000;
    public static Integer POST_GROUP_SIZE = 1000;

    public static <T, U, V> List<V> search(Function<U, ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>>> searchMethod,
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
                Boolean searchFinished = false;
                while (!searchFinished) {
                    BrAPIAcceptedSearchResponse searchResult = response.getBody().getRight().get();

                    // TODO: Check if we have more to get for pages
                    ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> searchGetResponse = searchGetMethod.apply(searchResult.getResult().getSearchResultsDbId(), 0, RESULTS_PER_QUERY);
                    if (searchGetResponse.getBody().getLeft().isPresent()) {
                        searchFinished = true;
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

    public static <T> List<T> post(List<T> brapiObjects,
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
                List<T> postChunk = brapiObjects.size() > (currentRightBorder + POST_GROUP_SIZE) ?
                        brapiObjects.subList(currentRightBorder, currentRightBorder + POST_GROUP_SIZE) :
                        brapiObjects.subList(currentRightBorder, brapiObjects.size());
                // Update our progress in the db
                if (upload != null) {
                    upload.updateProgress(finished, postChunk.size());
                    progressUpdateMethod.accept(upload);
                }
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
                finished += data.size();
                currentRightBorder += POST_GROUP_SIZE;
            }

            if (upload != null) {
                upload.updateProgress(listResult.size(), 0);
                progressUpdateMethod.accept(upload);
            }

            return listResult;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerException(e.toString(), e);
        }
    }

    public static <T> List<T> post(List<T> brapiObjects,
                                   Function<List<T>, ApiResponse> postMethod) throws ApiException {
        return post(brapiObjects, null, postMethod, null);
    }
}
