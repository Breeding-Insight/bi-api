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
import org.apache.commons.lang3.tuple.Pair;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIAcceptedSearchResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class Utilities {

    public static <T> Optional<T> findInList(List<T> checkList, T objectToCheck, Function<T, UUID> getterMethod){

        Optional<T> existingObject = checkList.stream()
                .filter(p -> getterMethod.apply(p).equals(getterMethod.apply(objectToCheck)))
                .findFirst();
        return existingObject;
    }

    public static <T> T handleBrapiSearchResponse(ApiResponse<Pair<Optional<T>, Optional<BrAPIAcceptedSearchResponse>>> response,
                                                  BrapiCallable<T> getSearchResultFn) {
        if (response.getBody().getLeft().isPresent()) {
            return response.getBody().getLeft().get();
        } else if (response.getBody().getRight().isPresent()) {
            // make request for data
            BrAPIAcceptedSearchResponse accepted = response.getBody().getRight().get();
            try {
                ApiResponse<T> result = getSearchResultFn.getBrapiSearchResult(accepted.getResult().getSearchResultsDbId());
                if (result.getStatusCode() == 202) {
                    // TODO: handle asynchronous search or just use async brapi client call when available
                    throw new InternalServerException("Expected search results to be ready");
                }
                return result.getBody();
            } catch (ApiException e) {
                throw new InternalServerException("BrAPI search results get failed");
            }
        } else {
            throw new InternalServerException("Invalid BrAPI search response");
        }
    }
}
