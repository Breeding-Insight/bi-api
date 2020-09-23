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
package org.breedinginsight.api.v1.controller.search;

import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.brapi.client.v2.model.exceptions.HttpBadRequestException;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.v1.controller.metadata.PaginateSort;
import org.breedinginsight.api.v1.controller.search.mappers.FilterField;
import org.breedinginsight.api.v1.controller.search.mappers.SearchMapper;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.MetadataFilter;
import org.reactivestreams.Publisher;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Filter("/**")
public class SearchFilter extends OncePerRequestHttpServerFilter {

    // Search filter should go before metadata filter
    public static int ORDER = MetadataFilter.ORDER + 1;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {

        Flowable<MutableHttpResponse<?>> requestFlow = Flowable.fromCallable(() -> true)
                .subscribeOn(Schedulers.io())
                .switchMap(aBoolean -> chain.proceed(request));

        RouteMatch routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
        if (routeMatch instanceof  MethodBasedRouteMatch) {
            var methodRoute = ((MethodBasedRouteMatch) routeMatch);
            if (methodRoute.hasAnnotation(Search.class)) {

                // Get the class for filter and sorting specified in annotation
                SearchMapper mapper;
                try {
                    Class mapperClass = methodRoute.getAnnotation(Search.class).getRequiredValue("using", Class.class);
                    Constructor mapperClassConstructor = mapperClass.getConstructor();
                    mapper = (SearchMapper) mapperClassConstructor.newInstance();
                } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                    throw new HttpServerException("Unable to create mapper class");
                }

                List<FilterField> filterFields = getAndValidateFilterFields(request, mapper);

                return requestFlow.doOnNext(res -> search(res, filterFields, mapper));
            }
        }

        return chain.proceed(request);
    }

    public void search(MutableHttpResponse res, List<FilterField> filterFields, SearchMapper mapper) {

        // Get the result
        Response response = (Response) res.body();
        if (response != null) {
            DataResponse dataResponse = (DataResponse) response.getResult();
            if (dataResponse != null) {
                List<?> data = dataResponse.getData();
                // Get the request body
                if (filterFields.size() > 0){
                    // Apply filters
                    // TODO: Catch bad filtering
                    data = mapper.filter(data, filterFields);
                }

                // Set the altered data in the response
                dataResponse.setData(data);
                ((Response) res.body()).setResult(dataResponse);

            }
        }
    }

    public List<FilterField> getAndValidateFilterFields(HttpRequest request, SearchMapper mapper) {

        Optional<SearchRequest> optionalSearchRequest = request.getBody(SearchRequest.class);
        if (!optionalSearchRequest.isPresent()) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Search body not provided");
        }

        SearchRequest searchRequest = optionalSearchRequest.get();
        List<FilterField> filterFields;
        try {
            filterFields = searchRequest.getFilter().stream()
                    .map(filter -> new FilterField(mapper.getField(filter.getField()), filter.getValue()))
                    .collect(Collectors.toList());
        } catch (NullPointerException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Search field does not exist");
        }

        return filterFields;
    }
}

