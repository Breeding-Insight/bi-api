/*
 * See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
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
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.services.exceptions.MissingMetadataException;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;

@Filter("/**")
public class MetadataFilter implements HttpServerFilter {

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {

        // Checks if we should even use the filter
        return Flowable.fromCallable(() -> true)
                .subscribeOn(Schedulers.io())
                .switchMap(aBoolean -> chain.proceed(request))
                .doOnNext(res -> {

                    // First check if we annotated the controller method for metadata filtering
                    RouteMatch routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
                    if (routeMatch instanceof  MethodBasedRouteMatch){
                        var methodRoute = ((MethodBasedRouteMatch) routeMatch);
                        if (methodRoute.hasAnnotation(AddMetadata.class)) {

                            try {
                                processMetadata(res);
                            } catch (MissingMetadataException e) {
                                throw e;
                            }

                        }
                    }

                });

    }

    private void processMetadata(MutableHttpResponse<?> res) throws MissingMetadataException {

        // Check if it is a 200
        if (res.status() == HttpStatus.OK){

            // Check if it is a response class
            if (res.body() instanceof Response){

                Response body = (Response) res.body();

                // Check if the metadata is already populated
                if (body.getMetadata() == null){
                    Metadata metadata = new Metadata();
                    Pagination pagination;
                    List<Status> metadataStatus = new ArrayList<>();

                    // Check there isn't more than one result
                    if (!( body.getResult() instanceof DataResponse )){
                        pagination = new Pagination(1, 1, 1, 0);
                    }
                    else {
                        throw new MissingMetadataException("Metadata was not properly populated");
                    }

                    metadata.setPagination(pagination);

                    // Set our status to a successful status
                    metadataStatus.add(new Status(StatusCode.INFO, "Query Successful"));
                    metadata.setStatus(metadataStatus);

                    ((Response) res.body()).setMetadata(metadata);
                }


            }
        }

    }
}