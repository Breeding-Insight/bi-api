package org.breedinginsight.brapi.v2;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.types.files.StreamedFile;
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
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapi.v2.model.request.query.ListQuery;
import org.breedinginsight.brapi.v2.services.BrAPIListService;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.delta.DeltaListDetails;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.AbstractQueryMapper;
import org.breedinginsight.utilities.response.mappers.ListQueryMapper;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    ) throws DoesNotExistException, ApiException {
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

    @Get("/lists/{listDbId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    @SuppressWarnings("unchecked")
    public <T extends BrapiQuery, U> HttpResponse<Response<DataResponse<List<U>>>> getListById(
            @PathVariable("programId") UUID programId,
            @PathVariable("listDbId") String listDbId,
            HttpRequest<?> request) {
        try {
            // Get the list from the BrAPI service
            DeltaListDetails details = brapiListService.getDeltaListDetails(listDbId, programId);

            // Get a new instance of BrAPI query matching the type of list contents
            T queryParams = (T) details.getQuery();

            // Bind query parameters to the object
            bindQueryParams(queryParams, request);

            // Perform standard bean validation
            Set<ConstraintViolation<Object>> violations = validator.validate(queryParams);
            if (!violations.isEmpty()) {
                List<String> errorMessages = violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toList());
                log.info(String.join(", ", errorMessages));
                return HttpResponse.status(HttpStatus.BAD_REQUEST, "Error with list contents search parameters");
            }

            // Fetch the list contents from the BrAPI service
            List<U> listContentsBrAPIObjects = (List<U>) details.getDataObjects();

            // Construct a search request for sorting the list contents
            SearchRequest searchRequest = details.constructSearchRequest(queryParams);

            // Get the map used to connect query sorting keys to contents object values
            AbstractQueryMapper contentsQueryMapper = details.getQueryMapper();

            return ResponseUtils.getBrapiQueryResponse(listContentsBrAPIObjects, contentsQueryMapper, queryParams, searchRequest);
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving list records");
        }
    }

    @Get("/lists/{listDbId}/export{?fileExtension}")
    @Produces(value = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<StreamedFile> germplasmListExport(
            @PathVariable("programId") UUID programId, @PathVariable("listDbId") String listDbId, @QueryValue(defaultValue = "XLSX") String fileExtension) {
        String downloadErrorMessage = "An error occurred while generating the download file. Contact the development team at bidevteam@cornell.edu.";
        try {
            // Get the list from the BrAPI service
            DeltaListDetails details = brapiListService.getDeltaListDetails(listDbId, programId);

            FileType extension = Enum.valueOf(FileType.class, fileExtension);
            DownloadFile listContentsFile = details.exportListObjects(extension);
            return HttpResponse.ok(listContentsFile.getStreamedFile()).header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename="+listContentsFile.getFileName()+extension.getExtension());
        }
        catch (Exception e) {
            log.info(e.getMessage(), e);
            e.printStackTrace();
            HttpResponse response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, downloadErrorMessage).contentType(MediaType.TEXT_PLAIN).body(downloadErrorMessage);
            return response;
        }
    }

    private void bindQueryParams(BrapiQuery queryParams, HttpRequest<?> request) {
        BeanIntrospection<BrapiQuery> introspection = BeanIntrospection.getIntrospection(BrapiQuery.class);
        for (BeanProperty<BrapiQuery, Object> property : introspection.getBeanProperties()) {
            String paramName = property.getName();
            if (request.getParameters().contains(paramName)) {
                String paramValue = request.getParameters().get(paramName);
                Object convertedValue;
                Class<?> propertyType = property.getType();

                if (propertyType.isEnum()) {
                    convertedValue = convertToEnum(paramValue, (Class<? extends Enum<?>>) propertyType);
                } else {
                    convertedValue = convertValue(paramValue, propertyType);
                }

                property.set(queryParams, convertedValue);
            }
        }
    }

    private <T extends Enum<T>> T convertToEnum(String value, Class<? extends Enum<?>> enumClass) {
        if (value == null) {
            return null;
        }
        return Enum.valueOf((Class<T>) enumClass, value.toUpperCase());
    }


    // Convert, if necessary, the values of query parameters to match the type defined for the fields in the BrapiQuery class
    private Object convertValue(String value, Class<?> targetType) {
        // Implement type conversion logic here
        // Other list content types might need more complex logic
        if (targetType == String.class) return value;
        if (targetType == Integer.class) return Integer.parseInt(value);
        if (targetType == Long.class) return Long.parseLong(value);
        if (targetType == Boolean.class) return Boolean.parseBoolean(value);
        // Add more type conversions as needed
        return value;
    }
}
