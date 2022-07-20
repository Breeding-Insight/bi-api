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

package org.breedinginsight.utilities.response;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.apache.commons.lang3.tuple.Pair;
import org.breedinginsight.api.model.v1.request.query.PaginationParams;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.SortOrder;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.model.ProgramUpload;
import org.breedinginsight.utilities.response.mappers.AbstractQueryMapper;
import org.breedinginsight.utilities.response.mappers.FilterField;
import se.sawano.java.text.AlphanumericComparator;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ResponseUtils {

    public final static SortOrder DEFAULT_SORT_ORDER = SortOrder.ASC;

    // All
    public static <T> HttpResponse<Response<DataResponse<T>>> getQueryResponse(
            List data, AbstractQueryMapper mapper, SearchRequest searchRequest, QueryParams queryParams) {
        return processSearchResponse(data, searchRequest, queryParams, mapper, new Metadata());
    }

    // Pagination and sort only
    public static <T> HttpResponse<Response<DataResponse<T>>> getQueryResponse(
            List data, AbstractQueryMapper mapper, QueryParams queryParams) {
        return processSearchResponse(data, null, queryParams, mapper, new Metadata());
    }

    // Brapi pagination and filter only
    public static <T> HttpResponse<Response<DataResponse<T>>> getBrapiQueryResponse(
            List data, AbstractQueryMapper mapper, BrapiQuery queryParams, SearchRequest searchRequest) {
        return processBrapiResponse(data, searchRequest, queryParams, mapper, new Metadata());
    }

    public static <T> HttpResponse<Response<DataResponse<T>>> getBrapiQueryResponse(
            List data, AbstractQueryMapper mapper, BrapiQuery queryParams) {
        return processBrapiResponse(data, null, queryParams, mapper, new Metadata());
    }

    // All
    public static HttpResponse<Response<ProgramUpload>> getUploadQueryResponse(
            ProgramUpload upload, AbstractQueryMapper mapper, SearchRequest searchRequest, QueryParams queryParams) {
        return processUploadSearchResponse(upload, searchRequest, queryParams, mapper, new Metadata());
    }

    // Pagination and sort only
    public static HttpResponse<Response<ProgramUpload>> getUploadQueryResponse(
            ProgramUpload upload, AbstractQueryMapper mapper, QueryParams queryParams) {
        return processUploadSearchResponse(upload, null, queryParams, mapper, new Metadata());
    }

    public static <T> HttpResponse<Response<T>> getSingleResponse(Object data) {
        Metadata metadata = constructMetadata(new Metadata(), new Pagination(1,1,1,1));
        return HttpResponse.ok(new Response(metadata, data));
    }

    private static <T> HttpResponse<Response<DataResponse<T>>> processSearchResponse(
            List data, SearchRequest searchRequest, QueryParams queryParams, AbstractQueryMapper mapper, Metadata metadata) {

        if (searchRequest != null){
            data = search(data, searchRequest, mapper);
        }
        data = sort(data, queryParams, mapper);
        Pair<List, Pagination> paginationResult = paginateData(data, queryParams);
        metadata = constructMetadata(metadata, paginationResult.getRight());
        return HttpResponse.ok(new Response(metadata, new DataResponse(paginationResult.getLeft())));
    }

    private static <T> HttpResponse<Response<DataResponse<T>>> processBrapiResponse(
            List data, SearchRequest searchRequest, BrapiQuery queryParams, AbstractQueryMapper mapper, Metadata metadata) {

        if (searchRequest != null){
            data = search(data, searchRequest, mapper);
        }
        QueryParams sortParams = new QueryParams();
        sortParams.setSortField(queryParams.getSortField());
        sortParams.setSortOrder(queryParams.getSortOrder());
        data = sort(data, sortParams, mapper);
        Pair<List, Pagination> paginationResult = paginateData(data, queryParams);
        metadata = constructMetadata(metadata, paginationResult.getRight());
        return HttpResponse.ok(new Response(metadata, new DataResponse(paginationResult.getLeft())));
    }

    private static <T> HttpResponse<Response<ProgramUpload>> processUploadSearchResponse(
            ProgramUpload upload, SearchRequest searchRequest, QueryParams queryParams, AbstractQueryMapper mapper, Metadata metadata) {

        List data = upload.getParsedData();
        if (searchRequest != null){
            data = search(data, searchRequest, mapper);
        }

        data = sort(data, queryParams, mapper);
        Pair<List, Pagination> paginationResult = paginateData(data, queryParams);
        metadata = constructMetadata(metadata, paginationResult.getRight());
        upload.setParsedData(paginationResult.getLeft());
        return HttpResponse.ok(new Response(metadata, upload));
    }

    private static List sort(List data, QueryParams queryParams, AbstractQueryMapper mapper) {

        if (queryParams.getSortField() != null) {
            Function field;
            try {
                field = mapper.getField(queryParams.getSortField());
            } catch (NullPointerException e) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Sort field does not exist");
            }

            SortOrder sortOrder = queryParams.getSortOrder() != null ? queryParams.getSortOrder() : ResponseUtils.DEFAULT_SORT_ORDER; ;

            GenericComparator comparator = new GenericComparator(
                    Comparator.nullsFirst(new AlphanumericComparator(Locale.ENGLISH)));

            // Convert everything to string. Might not be perfect, such as for BigDecimal, but it gets the job done.
            Function typeSafeField;
            if (data.size() >= 1 && (field.apply(data.get(0)) instanceof List || field.apply(data.get(0)) instanceof OffsetDateTime)) {
                typeSafeField = field;
            } else {
                typeSafeField = (Object object) -> field.apply(object) != null ? field.apply(object).toString() : null;
            }

            // Special comparators
            if (data.size() >= 1 && field.apply(data.get(0)) instanceof OffsetDateTime) {
                comparator = new GenericComparator(
                        Comparator.nullsFirst(Comparator.naturalOrder()));
            }

            Collections.sort(data,
                    Comparator.comparing(typeSafeField,
                            comparator));

            if (sortOrder == SortOrder.DESC) {
              Collections.reverse(data);
            }
        }

        return data;
    }

    private static List search(List<?> data, SearchRequest searchRequest, AbstractQueryMapper mapper) {

        List<FilterField> filterFields = new ArrayList<>();
        if (searchRequest.getFilters() != null) {
            filterFields = searchRequest.getFilters().stream()
                    .map(filter -> new FilterField(mapper.getField(filter.getField()), filter.getValue()))
                    .collect(Collectors.toList());
        }

        if (filterFields.size() > 0){
            // Apply filters
            List<FilterField> finalFilterFields = filterFields;
            return data.stream()
                    .filter(record ->
                            finalFilterFields.stream().allMatch(filterField -> {
                                if (filterField.getField().apply(record) == null) {
                                    return false;
                                } else if (filterField.getField().apply(record) instanceof List ||
                                        filterField.getField().apply(record) instanceof Object[]){

                                    List recordList = filterField.getField().apply(record) instanceof Object[] ?
                                            Arrays.asList((Object[]) filterField.getField().apply(record)) :
                                            ((List) filterField.getField().apply(record));

                                    return recordList.stream()
                                            .anyMatch(listValue ->
                                                    listValue.toString().toLowerCase().contains(filterField.getValue().toLowerCase()));
                                } else {
                                    return filterField.getField().apply(record).toString()
                                            .toLowerCase().contains(filterField.getValue().toLowerCase());
                                }
                            })
                    )
                    .collect(Collectors.toList());
        }

        return data;
    }

    private static Pair<List, Pagination> paginateData(List<?> data, PaginationParams paginationRequest) {

        Pagination pagination = new Pagination();
        Integer originalCount = data.size();

        // Show all by default
        Integer page = paginationRequest.getPage() != null ? paginationRequest.getPage() : paginationRequest.getDefaultPage();
        Integer pageSize = paginationRequest.getPageSize() != null ? paginationRequest.getPageSize() : paginationRequest.getDefaultPageSize();

        Integer pageAdjustedByIndex = page - paginationRequest.getDefaultPage();
        Integer startIndex = pageAdjustedByIndex * pageSize;
        if (startIndex > data.size() || startIndex < 0) {
            return Pair.of(new ArrayList<>(),
                    new Pagination(0, 0, 1, page));
        }

        Integer endIndex = startIndex + pageSize >= data.size() ?
                data.size() : startIndex + pageSize;

        data = data.subList(startIndex, endIndex);
        pagination.setCurrentPage(page);
        pagination.setPageSize(pageSize);
        pagination.setTotalPages((int) Math.ceil(originalCount / (double) pageSize));
        pagination.setTotalCount(originalCount);

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
