package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.query.TraitsQuery;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.brapi.BrAPIService;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class TraitController {

    @Inject
    private TraitService traitService;

    @Get("/programs/{programId}/traits{?traitsQuery*}")
    @Produces(MediaType.APPLICATION_JSON)
    @BrAPIService
    @Secured({SecurityRule.IS_AUTHENTICATED})
    public HttpResponse<Response<DataResponse<Trait>>> getTraits(@PathVariable UUID programId,
                                                                 @Valid TraitsQuery traitsQuery) {

        try {

            List<Trait> traits = traitService.getByProgramId(programId, traitsQuery.getFull());

            //TODO: Add in pagination
            List<Status> metadataStatus = new ArrayList<>();
            // Users query successfully
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            // Construct our metadata and response
            //TODO: Put in the actual page size
            Pagination pagination = new Pagination(traits.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);
            Response<DataResponse<Trait>> response = new Response<>(metadata, new DataResponse<>(traits));

            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            return HttpResponse.notFound();
        }
    }

    @Get("/programs/{programId}/traits/{traitId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @BrAPIService
    @Secured({SecurityRule.IS_AUTHENTICATED})
    public HttpResponse<Response<Trait>> getTraits(@PathVariable UUID programId, @PathVariable UUID traitId) {

        try {
            Optional<Trait> trait = traitService.getById(programId, traitId);
            if (trait.isPresent()) {
                Response<Trait> response = new Response<>(trait.get());
                return HttpResponse.ok(response);
            } else {
                return HttpResponse.notFound();
            }
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        }

    }
}
