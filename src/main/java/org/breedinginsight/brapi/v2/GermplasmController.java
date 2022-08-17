package org.breedinginsight.brapi.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.client.v2.modules.germplasm.GermplasmApi;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.germ.BrAPIParentType;
import org.brapi.v2.model.germ.BrAPIPedigreeNode;
import org.brapi.v2.model.germ.BrAPIPedigreeNodeParents;
import org.brapi.v2.model.germ.response.BrAPIGermplasmPedigreeResponse;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.api.model.v1.validators.SearchValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapi.v2.model.request.query.GermplasmQuery;
import org.breedinginsight.brapi.v2.model.response.mappers.GermplasmQueryMapper;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.response.ResponseUtils;

import javax.inject.Inject;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class GermplasmController {

    private final BrAPIGermplasmService germplasmService;
    private final GermplasmQueryMapper germplasmQueryMapper;
    private final ProgramDAO programDAO;


    @Inject
    public GermplasmController(BrAPIGermplasmService germplasmService, GermplasmQueryMapper germplasmQueryMapper, ProgramDAO programDAO) {
        this.germplasmService = germplasmService;
        this.germplasmQueryMapper = germplasmQueryMapper;
        this.programDAO = programDAO;
    }

    @Post("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/search/germplasm{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<List<BrAPIGermplasm>>>> searchGermplasm(
            @PathVariable("programId") UUID programId,
            @QueryValue @QueryValid(using = GermplasmQueryMapper.class) @Valid BrapiQuery queryParams,
            @Body @SearchValid(using = GermplasmQueryMapper.class) SearchRequest searchRequest) {
        try {
            log.debug("fetching germ for program: " + programId);
            List<BrAPIGermplasm> germplasm = germplasmService.getGermplasm(programId);
            queryParams.setSortField(germplasmQueryMapper.getDefaultSortField());
            queryParams.setSortOrder(germplasmQueryMapper.getDefaultSortOrder());
            return ResponseUtils.getBrapiQueryResponse(germplasm, germplasmQueryMapper, queryParams, searchRequest);
        } catch (ApiException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving germplasm");
        }
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/germplasm{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<List<BrAPIGermplasm>>>> getGermplasm(
            @PathVariable("programId") UUID programId,
            @QueryValue @QueryValid(using = GermplasmQueryMapper.class) @Valid GermplasmQuery queryParams) {
        try {
            log.debug("fetching germ for program: " + programId);

            List<BrAPIGermplasm> germplasm = germplasmService.getGermplasm(programId);
            SearchRequest searchRequest = queryParams.constructSearchRequest();
            return ResponseUtils.getBrapiQueryResponse(germplasm, germplasmQueryMapper, queryParams, searchRequest);
        } catch (ApiException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving germplasm");
        }
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}/germplasm/lists/{listDbId}/export{?fileExtension}")
    @Produces(value = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<StreamedFile> germplasmListExport(
            @PathVariable("programId") UUID programId, @PathVariable("listDbId") String listDbId, @QueryValue(defaultValue = "XLSX") String fileExtension) {
        String downloadErrorMessage = "An error occurred while generating the download file. Contact the development team at bidevteam@cornell.edu.";
        try {
            FileType extension = Enum.valueOf(FileType.class, fileExtension);
            DownloadFile germplasmListFile = germplasmService.exportGermplasmList(programId, listDbId, extension);
            HttpResponse<StreamedFile> germplasmListExport = HttpResponse.ok(germplasmListFile.getStreamedFile()).header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename="+germplasmListFile.getFileName()+extension.getExtension());
            return germplasmListExport;
        }
        catch (Exception e) {
            log.info(e.getMessage(), e);
            e.printStackTrace();
            HttpResponse response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, downloadErrorMessage).contentType(MediaType.TEXT_PLAIN).body(downloadErrorMessage);
            return response;
        }
    }

    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/germplasm/{germplasmId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<BrAPIGermplasm>> getSingleGermplasm(
            @PathVariable("programId") UUID programId,
            @PathVariable("germplasmId") String germplasmId) {
        try {
            log.debug("fetching germ id:" +  germplasmId +" for program: " + programId);
            Response<BrAPIGermplasm> response = new Response(germplasmService.getGermplasmByUUID(programId, germplasmId));
            return HttpResponse.ok(response);
        } catch (InternalServerException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving germplasm");
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, "Germplasm not found");
        }
    }


    //todo add queryparams for includeSiblings and notation
    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/germplasm/{germplasmId}/pedigree{?notation}{?includeSiblings}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<BrAPIGermplasmPedigreeResponse> getGermplasmPedigreeInfo(
            @PathVariable("programId") UUID programId,
            @PathVariable("germplasmId") String germplasmId,
            @QueryValue(defaultValue = "") String notation,
            @QueryValue(defaultValue = "false") Boolean includeSiblings) {
        try {
            log.debug("fetching pedigree for germ id:" +  germplasmId +" for program: " + programId);
            BrAPIPedigreeNode returnNode;
            BrAPIGermplasmPedigreeResponse response;
            BrAPIMetadata metadata;
            if (germplasmId == "0") {
                //todo check germplasmId is germplasmDbId
                //Unknown germplasm node
                returnNode = new BrAPIPedigreeNode();
                returnNode.setGermplasmDbId("0");
                returnNode.setGermplasmName("Unknown");
                returnNode.setParents(null);
                returnNode.setSiblings(null);
                returnNode.setPedigree(null);
                metadata = new BrAPIMetadata();
                response = new BrAPIGermplasmPedigreeResponse();
            } else {
                BrAPIGermplasm germplasm = germplasmService.getGermplasmByDBID(programId, germplasmId);
                //Forward the pedigree call to the backing BrAPI system of the program passing the germplasmDbId that came in the request
                GermplasmApi api = new GermplasmApi(programDAO.getCoreClient(programId));
                ApiResponse<BrAPIGermplasmPedigreeResponse> pedigreeResponse = api.germplasmGermplasmDbIdPedigreeGet(germplasmId, notation, includeSiblings);
                returnNode = pedigreeResponse.getBody().getResult();
                metadata = pedigreeResponse.getBody().getMetadata();
                response = pedigreeResponse.getBody();

                //Add nodes for unknown parents if applicable
                if (germplasm.getAdditionalInfo().has("femaleParentUnknown") && germplasm.getAdditionalInfo().get("femaleParentUnknown").getAsBoolean()) {
                    BrAPIPedigreeNodeParents unknownFemale = new BrAPIPedigreeNodeParents();
                    unknownFemale.setGermplasmDbId("0");
                    unknownFemale.setGermplasmName("Unknown");
                    unknownFemale.setParentType(BrAPIParentType.FEMALE);
                    returnNode.addParentsItem(unknownFemale);
                }
                if (germplasm.getAdditionalInfo().has("maleParentUnknown") && germplasm.getAdditionalInfo().get("maleParentUnknown").getAsBoolean()) {
                    BrAPIPedigreeNodeParents unknownMale = new BrAPIPedigreeNodeParents();
                    unknownMale.setGermplasmDbId("0");
                    unknownMale.setGermplasmName("Unknown");
                    unknownMale.setParentType(BrAPIParentType.FEMALE);
                    returnNode.addParentsItem(unknownMale);
                }
            }
            response.setResult(returnNode);
            return HttpResponse.ok(response);
        } catch (InternalServerException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pedigree node");
        } catch (DoesNotExistException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, "Pedigree node not found");
        } catch (ApiException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pedigree node");
        }
    }

}
