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

package org.breedinginsight.services;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.apache.commons.lang3.tuple.Pair;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.search.mappers.FilterField;
import org.breedinginsight.api.v1.controller.search.mappers.SearchMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ResponseService {

    // All
    public static <T> HttpResponse<Response<DataResponse<T>>> getQueryResponse(
            List data, SearchMapper mapper, SearchRequest searchRequest, QueryParams queryParams) {
        return processSearchResponse(data, searchRequest, queryParams, mapper, new Metadata());
    }

    // Pagination and sort only
    public static <T> HttpResponse<Response<DataResponse<T>>> getQueryResponse(
            List data, SearchMapper mapper, QueryParams queryParams) {
        return processSearchResponse(data, null, queryParams, mapper, new Metadata());
    }

    public static <T> HttpResponse<Response<T>> processSingleResponse(Object data) {
        Metadata metadata = constructMetadata(new Metadata(), new Pagination(1,1,1,1));
        return HttpResponse.ok(new Response(metadata, data));
    }

    private static <T> HttpResponse<Response<DataResponse<T>>> processSearchResponse(
            List data, SearchRequest searchRequest, QueryParams queryParams, SearchMapper mapper, Metadata metadata) {

        if (searchRequest != null){
            data = search(data, searchRequest, mapper);
        }
        data = sort(data, queryParams, mapper);
        Pair<List, Pagination> paginationResult = paginateData(data, queryParams);
        metadata = constructMetadata(metadata, paginationResult.getRight());
        return HttpResponse.ok(new Response(metadata, new DataResponse(paginationResult.getLeft())));
    }

    private static List sort(List data, QueryParams queryParams, SearchMapper mapper) {

        if (queryParams.getSortField() != null) {
            Function field;
            try {
                field = mapper.getField(queryParams.getSortField());
            } catch (NullPointerException e) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Sort field does not exist");
            }
            data = mapper.sort(data, field, queryParams.getSortOrder());
        }

        return data;
    }

    private static List search(List<?> data, SearchRequest searchRequest, SearchMapper mapper) {

        List<FilterField> filterFields = searchRequest.getFilter().stream()
                .map(filter -> new FilterField(mapper.getField(filter.getField()), filter.getValue()))
                .collect(Collectors.toList());

        if (filterFields.size() > 0){
            // Apply filters
            // TODO: Catch bad filtering
            data = mapper.filter(data, filterFields);
        }

        return data;
    }

    private static Pair<List, Pagination> paginateData(List<?> data, QueryParams paginationRequest) {

        Pagination pagination = new Pagination();
        Integer originalCount = data.size();

        if (!paginationRequest.isShowAll()) {
            Integer pageAdjustedByIndex = paginationRequest.getPage() - 1;
            Integer startIndex = pageAdjustedByIndex * paginationRequest.getPageSize();
            if (startIndex > data.size() || startIndex < 0) {
                return Pair.of(new ArrayList<>(), new Pagination(0, 0, 1, 1));
            }

            Integer endIndex = startIndex + paginationRequest.getPageSize() >= data.size() ?
                    data.size() : startIndex + paginationRequest.getPageSize();
            data = data.subList(startIndex, endIndex);
        }

        pagination.setPageSize(data.size());
        pagination.setTotalPages((int) Math.ceil(originalCount / (double) data.size()));
        pagination.setTotalCount(originalCount);
        pagination.setCurrentPage(paginationRequest.getPage());

        return Pair.of(data, pagination);
    }

    private static Metadata constructMetadata(Metadata metadata, Pagination pagination) {

        List<Status> metadataStatus = new ArrayList<>();
        if (metadata.getStatus() != null) {
            metadataStatus.addAll(metadata.getStatus());
        }

        // Set our status to a successful status
        metadata.setPagination(pagination);
        metadataStatus.add(new Status(StatusCode.INFO, "Query Successful"));
        metadata.setStatus(metadataStatus);
        return metadata;

    }
}
