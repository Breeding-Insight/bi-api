package org.breedinginsight.brapi.v2;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.model.request.query.ListQuery;
import org.breedinginsight.brapi.v2.services.BrAPIListService;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.ListQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.Validator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIListController {
    private final ProgramService programService;
    private final BrAPIListService brapiListService;
    private final ListQueryMapper listQueryMapper;
    private final Validator validator;

    @Inject
    public BrAPIListController(ProgramService programService, BrAPIListService brapiListService,
                               ListQueryMapper listQueryMapper, Validator validator) {
        this.programService = programService;
        this.brapiListService = brapiListService;
        this.listQueryMapper = listQueryMapper;
        this.validator = validator;
    }

    @Get("/lists{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<Object>>> getLists(
            @PathVariable("programId") UUID programId,
            @QueryValue @QueryValid(using = ListQueryMapper.class) @Valid ListQuery queryParams
    ) throws DoesNotExistException, ApiException {
        try {
            Program program = programService
                    .getById(programId)
                    .orElseThrow(() -> new DoesNotExistException("Program does not exist"));

            BrAPIListTypes type = BrAPIListTypes.fromValue(queryParams.getListType());
            String source = null;
            String id = null;
            if (queryParams.getExternalReferenceSource() != null && !queryParams.getExternalReferenceSource().isEmpty()) {
                source = queryParams.getExternalReferenceSource();
            }
            if (queryParams.getExternalReferenceId() != null && !queryParams.getExternalReferenceId().isEmpty()) {
                id = queryParams.getExternalReferenceId();
            }

            // If the date display format was sent as a query param, then update the query mapper.
            String dateFormatParam = queryParams.getDateDisplayFormat();
            if (dateFormatParam != null) {
                listQueryMapper.setDateDisplayFormat(dateFormatParam);
            }
            List<BrAPIListSummary> brapiLists = brapiListService.getListSummariesByTypeAndXref(type, source, id, program);
            SearchRequest searchRequest = queryParams.constructSearchRequest();

            return ResponseUtils.getBrapiQueryResponse(brapiLists, listQueryMapper, queryParams, searchRequest);

        } catch (IllegalArgumentException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, "Error parsing requested date format");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Delete("/lists/{listDbId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<Object>>> deleteListById(
            @PathVariable("programId") UUID programId,
            @PathVariable("listDbId") String listDbId,
            HttpRequest<Void> request
    ) {
        boolean hardDelete = false;
        if (request.getParameters().contains("hardDelete")) {
            String paramValue = request.getParameters().get("hardDelete");
            hardDelete = "true".equals(paramValue);
        }
        try {
            brapiListService.deleteBrAPIList(listDbId, programId, hardDelete);
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving germplasm list records");
        }
    }
}
