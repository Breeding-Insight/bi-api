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
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.SpeciesService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.jooq.exception.DataAccessException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class SpeciesController {

    @Inject
    private SpeciesService speciesService;

    @Get("/species")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<Species>>> getSpecies() {
        try {
            List<Species> species = speciesService.getAll();

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(species.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response response = new Response(metadata, new DataResponse<>(species));
            return HttpResponse.ok(response);
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }

    }

    @Get("/species/{speciesId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Species>> getSpecies(@PathVariable UUID speciesId) {

        try {
            Species species = speciesService.getById(speciesId);
            Response<Species> response = new Response<>(species);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e){
            return HttpResponse.notFound();
        } catch (DataAccessException e){
            log.error("Error executing query: {}", e.getMessage());
            return HttpResponse.serverError();
        }
    }
}
