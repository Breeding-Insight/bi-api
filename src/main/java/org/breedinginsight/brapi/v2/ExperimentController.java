package org.breedinginsight.brapi.v2;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapi.v2.services.BrAPITrialService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.ExperimentQueryMapper;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ExperimentController {

    private final BrAPITrialService experimentService;
    private final ExperimentQueryMapper experimentQueryMapper;

    @Inject
    public ExperimentController(BrAPITrialService experimentService, ExperimentQueryMapper experimentQueryMapper) {
        this.experimentService = experimentService;
        this.experimentQueryMapper = experimentQueryMapper;
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/trials{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<List<BrAPITrial>>>> getExperiments(
            @PathVariable("programId") UUID programId, @QueryValue BrapiQuery queryParams,
            @QueryValue boolean metadata) {
        try {
            log.debug("fetching trials for program: " + programId);

            List<BrAPITrial> experiments = experimentService.getExperiments(programId);
            return ResponseUtils.getBrapiQueryResponse(experiments, experimentQueryMapper, queryParams);

        } catch (ApiException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving experiments");
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
