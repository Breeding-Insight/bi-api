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

package org.breedinginsight.api.v1.controller.brapi;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.brapi.client.v2.model.exceptions.HttpInternalServerError;
import org.brapi.client.v2.model.exceptions.HttpNotFoundException;
import org.breedinginsight.services.brapi.BrAPIClientProvider;
import org.breedinginsight.model.ProgramBrAPIEndpoints;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

@Filter("/**")
public class BrAPIServiceFilter extends OncePerRequestHttpServerFilter {


    @Property(name = "brapi.server.core-url")
    private String defaultBrAPICoreUrl;
    @Property(name = "brapi.server.pheno-url")
    private String defaultBrAPIPhenoUrl;
    @Property(name = "brapi.server.geno-url")
    private String defaultBrAPIGenoUrl;
    @Inject
    private ProgramService programService;
    @Inject
    private BrAPIClientProvider brAPIClientProvider;

    @Override
    public int getOrder() {
        // We want our filter to go last so we have the client to inject
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {

        // Checks if we should even use the filter
        return Flowable.fromCallable(() -> true)
                .subscribeOn(Schedulers.io())
                .switchMap(aBoolean -> {

                    RouteMatch routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
                    Map<String, Object> params = routeMatch.getVariableValues();
                    if (params.get("programId") != null) {
                        if (routeMatch instanceof MethodBasedRouteMatch) {

                            UUID programId;
                            try {
                                programId = UUID.fromString(params.get("programId").toString());
                            } catch (IllegalArgumentException e) {
                                return Flowable.error(new HttpNotFoundException("Program does not exist"));
                            }

                            ProgramBrAPIEndpoints programBrAPIEndpoints;
                            try {
                                programBrAPIEndpoints = programService.getBrapiEndpoints(programId);
                            } catch (DoesNotExistException e) {
                                return Flowable.error(new HttpNotFoundException("Program does not exist"));
                            }
                            String coreUrl = getCoreUrl(programBrAPIEndpoints);
                            brAPIClientProvider.setCoreClient(coreUrl);
                            String phenoUrl = getPhenoUrl(programBrAPIEndpoints);
                            brAPIClientProvider.setPhenoClient(phenoUrl);
                            String genoUrl = getGenoUrl(programBrAPIEndpoints);
                            brAPIClientProvider.setGenoClient(genoUrl);
                            return chain.proceed(request);
                        } else {
                            // We shouldn't get here
                            return Flowable.error(new HttpInternalServerError("Unable to process request"));
                        }
                    } else {

                        // We'll get here for /programs. Use client defaults to begin with, they can change their
                        // brapi service later.
                        brAPIClientProvider.setCoreClient(defaultBrAPICoreUrl);
                        brAPIClientProvider.setPhenoClient(defaultBrAPIPhenoUrl);
                        brAPIClientProvider.setGenoClient(defaultBrAPIGenoUrl);
                        return chain.proceed(request);
                    }
                });
    }

    public String getCoreUrl(ProgramBrAPIEndpoints programBrAPIEndpoints){
        if (programBrAPIEndpoints.getCoreUrl().isPresent()){
            return programBrAPIEndpoints.getCoreUrl().get();
        } else {
            return defaultBrAPICoreUrl;
        }
    }

    public String getPhenoUrl(ProgramBrAPIEndpoints programBrAPIEndpoints){
        if (programBrAPIEndpoints.getPhenoUrl().isPresent()){
            return programBrAPIEndpoints.getPhenoUrl().get();
        } else {
            return defaultBrAPIPhenoUrl;
        }
    }

    public String getGenoUrl(ProgramBrAPIEndpoints programBrAPIEndpoints){
        if (programBrAPIEndpoints.getCoreUrl().isPresent()){
            return programBrAPIEndpoints.getGenoUrl().get();
        } else {
            return defaultBrAPIGenoUrl;
        }
    }
}
