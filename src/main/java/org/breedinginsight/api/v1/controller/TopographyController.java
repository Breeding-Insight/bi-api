package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.Topography;
import org.breedinginsight.services.TopographyService;
import org.jooq.exception.DataAccessException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class TopographyController {

    @Inject
    private TopographyService topographyService;

    @Get("/topography_options")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<Topography>>> getTopographies() {
        try {
            List<Topography> topographies = topographyService.getAll();

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(topographies.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response<DataResponse<Topography>> response = new Response(metadata, new DataResponse<>(topographies));
            return HttpResponse.ok(response);
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }

    @Get("/topography_options/{topographyId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Topography>> getTopography(@PathVariable UUID topographyId) {

        try {
            Optional<Topography> topography = topographyService.getById(topographyId);
            if(topography.isPresent()) {
                Response<Topography> response = new Response(topography.get());
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.notFound();
            }

        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }
}
