package org.breedinginsight.api.v1.controller;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.breedinginsight.model.BrAPIClientProvider;
import org.reactivestreams.Publisher;

import javax.inject.Inject;

@Filter("/**")
public class BrAPIServerFilter implements HttpServerFilter {

    @Value("brapi.server.url")
    String defaultBrAPIUrl;

    @Inject
    BrAPIClientProvider brAPIClientProvider;

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {

        // Checks if we should even use the filter
        return Flowable.fromCallable(() -> true)
                .subscribeOn(Schedulers.io())
                .switchMap(aBoolean -> {
                    //TODO: Lookup in the database to see if their url is different
                    brAPIClientProvider.setCoreClient(defaultBrAPIUrl);
                    brAPIClientProvider.setPhenoClient(defaultBrAPIUrl);
                    brAPIClientProvider.setGenoClient(defaultBrAPIUrl);
                    return chain.proceed(request);
                });
    }
}
