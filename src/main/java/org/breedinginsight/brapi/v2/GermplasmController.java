package org.breedinginsight.brapi.v2;

import com.google.gson.Gson;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.response.BrAPIGermplasmListResponse;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapi.v1.model.response.mappers.ObservationVariableQueryMapper;
import org.breedinginsight.brapi.v2.model.response.mappers.GermplasmQueryMapper;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.services.brapi.BrAPIProvider;
import org.breedinginsight.utilities.response.ResponseUtils;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class GermplasmController {

    private BrAPIGermplasmService germplasmService;
    private Gson gson;
    private GermplasmQueryMapper germplasmQueryMapper;

    @Inject
    public GermplasmController(BrAPIGermplasmService germplasmService, GermplasmQueryMapper germplasmQueryMapper) {
        this.germplasmService = germplasmService;
        this.germplasmQueryMapper = germplasmQueryMapper;
        this.gson = new Gson();
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/germplasm{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<String> getGermplasm(
            @PathVariable("programId") UUID programId,
            @QueryValue @QueryValid(using = GermplasmQueryMapper.class) @Valid BrapiQuery queryParams) {
        List<BrAPIGermplasm> germplasm = germplasmService.getGermplasm(programId);
        queryParams.setSortField(germplasmQueryMapper.getDefaultSortField());
        queryParams.setSortOrder(germplasmQueryMapper.getDefaultSortOrder());
        Response<DataResponse<Object>> response = ResponseUtils.getBrapiQueryResponse(germplasm, germplasmQueryMapper, queryParams).body();
        // Jackson can't serialize the additional info
        String respBody = gson.toJson(response);
        return HttpResponse.ok(respBody);

    }
}
