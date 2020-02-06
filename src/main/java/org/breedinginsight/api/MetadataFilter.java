package org.breedinginsight.api;

import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Filter("/**")
public class MetadataFilter implements HttpServerFilter {

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return Flowable.fromCallable(() -> true)
        .subscribeOn(Schedulers.io())
        .switchMap(aBoolean -> chain.proceed(request))
        .doOnNext(res -> {

            // Check if it is a 200
            if (res.status() == HttpStatus.OK){

                // Check if it is a response class
                if (res.body() instanceof Response){

                    // Check if the metadata is already populated
                    if (((Response) res.body()).getMetadata() == null){

                        Metadata metadata = new Metadata();
                        Pagination pagination;
                        List<Status> metadataStatus = new ArrayList<>();

                        // Check there isn't more than one result
                        if (!( ((Response) res.body()).getResult() instanceof DataResponse )){
                            pagination = new Pagination(1, 1, 1, 0);
                        }
                        else {
                            // Multiresult responses should have pagination set in controller
                            // because it takes specific knowledge. Set to all zero for response structure purposes.
                            pagination = new Pagination(0, 0, 0, 0);
                        }

                        metadata.setPagination(pagination);

                        // Set our status to a successful status
                        metadataStatus.add(new Status(StatusCode.INFO, "Query Successful"));
                        metadata.setStatus(metadataStatus);

                        ((Response) res.body()).setMetadata(metadata);
                    }


                }
            }

        });

    }

}