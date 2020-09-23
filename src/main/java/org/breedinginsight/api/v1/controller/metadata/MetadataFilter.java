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

package org.breedinginsight.api.v1.controller.metadata;

import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.exceptions.HttpException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.security.rules.$ConfigurationInterceptUrlMapRuleDefinitionClass;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.reactivex.Flowable;
import io.reactivex.functions.LongConsumer;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import org.brapi.client.v2.model.exceptions.HttpBadRequestException;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.search.mappers.SearchMapper;
import org.reactivestreams.Publisher;

import javax.management.Query;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Filter("/**")
public class MetadataFilter extends OncePerRequestHttpServerFilter {

    public static int ORDER = 0;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {

        // Set up our publisher
        Flowable<MutableHttpResponse<?>> requestFlow = Flowable.fromCallable(() -> true)
                .subscribeOn(Schedulers.io())
                .switchMap(aBoolean -> chain.proceed(request));

        RouteMatch routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
        if (routeMatch instanceof  MethodBasedRouteMatch) {
            var methodRoute = ((MethodBasedRouteMatch) routeMatch);
            if (methodRoute.hasAnnotation(PaginateSort.class)) {

                // Get the class for filter and sorting specified in annotation
                SearchMapper mapper;
                try {
                    Class mapperClass = methodRoute.getAnnotation(PaginateSort.class).getRequiredValue("using", Class.class);
                    Constructor mapperClassConstructor = mapperClass.getConstructor();
                    mapper = (SearchMapper) mapperClassConstructor.newInstance();
                } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                    throw new HttpServerException("Unable to create mapper class");
                }

                QueryParams queryParams = getAndValidateQueryParams(request, mapper);

                // Check that are query params are ok
                return requestFlow.doOnNext(res -> paginateAndSort(res, queryParams, mapper));
            }
        }

        return chain.proceed(request);
    }

    private void paginateAndSort(MutableHttpResponse res, QueryParams queryParams, SearchMapper mapper) {

        if (res.status() == HttpStatus.OK) {
            if (res.body() instanceof Response) {

                // Get existing metadata information
                Response body = (Response) res.body();
                Metadata metadata = body.getMetadata();
                if (metadata == null) {
                    metadata = new Metadata();
                }
                List<Status> metadataStatus = metadata.getStatus();
                if (metadataStatus == null) {
                    metadataStatus = new ArrayList<>();
                }

                if (body.getResult() instanceof DataResponse) {

                    DataResponse dataResponse = (DataResponse) body.getResult();
                    if (dataResponse != null) {
                        List<?> data = dataResponse.getData();

                        if (queryParams.getSortField() != null) {
                            Function field;
                            try {
                                field = mapper.getField(queryParams.getSortField());
                            } catch (NullPointerException e) {
                                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Sort field does not exist");
                            }
                            data = mapper.sort(data, field, queryParams.getSortOrder());
                        }

                        // Paginate records
                        List<?> paginatedData = paginate(data, queryParams);

                        // Create pagination object
                        Pagination pagination = new Pagination();
                        if (queryParams.isShowAll()){
                            pagination.setCurrentPage(1);
                            pagination.setTotalCount(data.size());
                            pagination.setPageSize(data.size());
                            pagination.setTotalPages(1);
                        } else {
                            pagination.setPageSize(queryParams.getPageSize());
                            pagination.setTotalCount(data.size());
                            pagination.setCurrentPage(queryParams.getPage());
                            pagination.setTotalPages((int) Math.ceil(data.size() / (double) queryParams.getPageSize()));
                        }

                        metadata.setPagination(pagination);
                        dataResponse.setData(paginatedData);
                        ((Response) res.body()).setResult(dataResponse);
                    }
                } else {
                    // Generate and return single pagination
                    metadata.setPagination(new Pagination(1, 1, 1, 1));
                }

                // Set our status to a successful status
                metadataStatus.add(new Status(StatusCode.INFO, "Query Successful"));
                metadata.setStatus(metadataStatus);

                ((Response) res.body()).setMetadata(metadata);
            }
        }
    }

    private List<?> paginate(List<?> data, QueryParams paginationRequest) {
        Integer pageAdjustedByIndex = paginationRequest.getPage() - 1;
        Integer startIndex = pageAdjustedByIndex * paginationRequest.getPageSize();
        if (startIndex > data.size() || startIndex < 0) return new ArrayList<>();
        Integer endIndex = startIndex + paginationRequest.getPageSize() >= data.size() ?
                data.size() - 1 : startIndex + paginationRequest.getPageSize();

        return data.subList(startIndex, endIndex);
    }

    private void setErrorState(MutableHttpResponse res, HttpStatus status, String message) {
        res.status(status, message);
        res.body(null);
    }

    private QueryParams getAndValidateQueryParams(HttpRequest request, SearchMapper mapper) throws HttpStatusException {

        HttpParameters parameters = request.getParameters();
        QueryParams queryParams = new QueryParams();
        Optional<Integer> page = parameters.get("page", Integer.class);
        Optional<Integer> pageSize = parameters.get("pageSize", Integer.class);
        Optional<Boolean> showAll = parameters.get("showAll", Boolean.class);
        Optional<String> sortField = parameters.get("sortField", String.class);
        Optional<String> sortOrderString = parameters.get("sortOrder", String.class);

        if (sortOrderString.isPresent()) {
            try {
                SortOrder sortOrder = SortOrder.valueOf(sortOrderString.get());
                queryParams.setSortOrder(sortOrder);
            } catch (IllegalArgumentException e) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Sort order not valid");
            }
        }

        if (pageSize.isPresent()) queryParams.setPageSize(pageSize.get());
        if (queryParams.getPageSize() < 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Page size must be above 0");
        }

        if (page.isPresent()) queryParams.setPage(page.get());
        if (showAll.isPresent()) queryParams.setShowAll(showAll.get());
        if (sortField.isPresent()) queryParams.setSortField(sortField.get());

        // Check that the mapper field is valid
        if (sortField.isPresent()){
            try {
                mapper.getField(sortField.get());
            }
            catch (NullPointerException e) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Sort field does not exist");
            }
        }

        return queryParams;
    }

}