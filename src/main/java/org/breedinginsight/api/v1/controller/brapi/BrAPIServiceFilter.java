package org.breedinginsight.api.v1.controller.brapi;

import io.micronaut.context.annotation.Value;
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
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

@Filter("/**/programs/**")
public class BrAPIServiceFilter extends OncePerRequestHttpServerFilter {


    @Value("${micronaut.brapi.server.url}")
    private String defaultBrAPIUrl;
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

                            //TODO: This service method should return a 404 if program isn't found
                            ProgramBrAPIEndpoints programBrAPIEndpoints = programService.getBrapiEndpoints(programId);
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
                        // The filter is not meant for this request
                        return chain.proceed(request);
                    }
                });
    }

    public String getCoreUrl(ProgramBrAPIEndpoints programBrAPIEndpoints){
        if (programBrAPIEndpoints.getCoreUrl().isPresent()){
            return programBrAPIEndpoints.getCoreUrl().get();
        } else {
            return defaultBrAPIUrl;
        }
    }

    public String getPhenoUrl(ProgramBrAPIEndpoints programBrAPIEndpoints){
        if (programBrAPIEndpoints.getPhenoUrl().isPresent()){
            return programBrAPIEndpoints.getPhenoUrl().get();
        } else {
            return defaultBrAPIUrl;
        }
    }

    public String getGenoUrl(ProgramBrAPIEndpoints programBrAPIEndpoints){
        if (programBrAPIEndpoints.getCoreUrl().isPresent()){
            return programBrAPIEndpoints.getGenoUrl().get();
        } else {
            return defaultBrAPIUrl;
        }
    }
}
