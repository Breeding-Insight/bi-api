package org.breedinginsight.brapi.v2;

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
import org.brapi.v2.model.BrAPIIndexPagination;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.BrAPIStatus;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.germ.*;
import org.brapi.v2.model.germ.request.BrAPIGermplasmSearchRequest;
import org.brapi.v2.model.germ.response.BrAPIGermplasmPedigreeResponse;
import org.brapi.v2.model.germ.response.BrAPIGermplasmProgenyResponse;
import org.brapi.v2.model.germ.response.BrAPIPedigreeListResponse;
import org.brapi.v2.model.germ.response.BrAPIPedigreeListResponseResult;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.api.model.v1.validators.SearchValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v1.model.request.query.BrapiQuery;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapi.v2.model.request.query.GermplasmQuery;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.utilities.Utilities;
import org.breedinginsight.utilities.response.mappers.GermplasmQueryMapper;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.brapps.importer.model.exports.FileType;
import org.breedinginsight.daos.ProgramDAO;
import org.breedinginsight.model.DownloadFile;
import org.breedinginsight.model.GermplasmGenotype;
import org.breedinginsight.services.brapi.BrAPIEndpointProvider;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.geno.GenotypeService;
import org.breedinginsight.utilities.response.ResponseUtils;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.*;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIGermplasmController {

    private final BrAPIGermplasmService germplasmService;
    private final GermplasmQueryMapper germplasmQueryMapper;
    private final ProgramDAO programDAO;
    private final BrAPIGermplasmDAO germplasmDAO;

    private final GenotypeService genoService;
    private final ProgramService programService;

    private final BrAPIEndpointProvider brAPIEndpointProvider;


    @Inject
    public BrAPIGermplasmController(BrAPIGermplasmService germplasmService,
                                    GermplasmQueryMapper germplasmQueryMapper,
                                    ProgramDAO programDAO,
                                    BrAPIGermplasmDAO germplasmDAO,
                                    GenotypeService genoService,
                                    BrAPIEndpointProvider brAPIEndpointProvider,
                                    ProgramService programService) {
        this.germplasmService = germplasmService;
        this.germplasmQueryMapper = germplasmQueryMapper;
        this.programDAO = programDAO;
        this.germplasmDAO = germplasmDAO;
        this.genoService = genoService;
        this.brAPIEndpointProvider = brAPIEndpointProvider;
        this.programService = programService;
    }

    // NOTE: bypasses cache and makes api request directly to brapi service
    @Post("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/search/germplasm")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<List<BrAPIGermplasm>>>> searchGermplasm(
            @PathVariable("programId") UUID programId,
            @Body BrAPIGermplasmSearchRequest body) {

        log.debug("searchGermplasm: fetching germplasm by filters");

        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.warn("Program id: " + programId + " not found");
            return HttpResponse.notFound();
        }

        // TODO
        return null;

        /*
        try {
            List<BrAPIPedigreeNode> pedigree = pedigreeDAO.getPedigree(
                    program.get(),
                    Optional.ofNullable(includeParents),
                    Optional.ofNullable(includeSiblings),
                    Optional.ofNullable(includeProgeny),
                    Optional.ofNullable(includeFullTree),
                    Optional.ofNullable(pedigreeDepth),
                    Optional.ofNullable(progenyDepth),
                    Optional.ofNullable(germplasmName));

            return HttpResponse.ok(
                    new BrAPIPedigreeListResponse()
                            .metadata(new BrAPIMetadata().pagination(new BrAPIIndexPagination().currentPage(0)
                                    .totalPages(1)
                                    .pageSize(pedigree.size())
                                    .totalCount(pedigree.size())))
                            .result(new BrAPIPedigreeListResponseResult().data(pedigree))
            );
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "error fetching pedigree");
        }
        */


    }

    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/germplasm{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<List<BrAPIGermplasm>>>> getGermplasm(
            @PathVariable("programId") UUID programId,
            @QueryValue @QueryValid(using = GermplasmQueryMapper.class) @Valid GermplasmQuery queryParams) {
        try {
            log.debug("fetching germ for program: " + programId);

            // If the date display format was sent as a query param, then update the query mapper.
            String dateFormatParam = queryParams.getDateDisplayFormat();
            if (dateFormatParam != null) {
                germplasmQueryMapper.setDateDisplayFormat(dateFormatParam);
            }

            List<BrAPIGermplasm> germplasm = germplasmService.getGermplasm(programId);
            SearchRequest searchRequest = queryParams.constructSearchRequest();
            return ResponseUtils.getBrapiQueryResponse(germplasm, germplasmQueryMapper, queryParams, searchRequest);
        } catch (ApiException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving germplasm");
        } catch (IllegalArgumentException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, "Error parsing requested date format");
        }
    }

    @Get("/programs/{programId}/germplasm/lists/{listDbId}/records{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<DataResponse<List<BrAPIGermplasm>>>> getGermplasmListRecords(
        @PathVariable("programId") UUID programId,
        @PathVariable("listDbId") String listDbId,
        @QueryValue @QueryValid(using = GermplasmQueryMapper.class) @Valid GermplasmQuery queryParams) {
        try {
            List<BrAPIGermplasm> germplasm = germplasmService.getGermplasmByList(programId, listDbId);
            SearchRequest searchRequest = queryParams.constructSearchRequest();
            return ResponseUtils.getBrapiQueryResponse(germplasm, germplasmQueryMapper, queryParams, searchRequest);
        } catch (Exception e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving germplasm list records");
        }
    }

    @Get("/programs/{programId}/germplasm/lists/{listDbId}/export{?fileExtension}")
    @Produces(value = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
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

    @Get("/programs/{programId}/germplasm/export{?fileExtension}")
    @Produces(value = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<StreamedFile> germplasmExport(
            @PathVariable("programId") UUID programId, @QueryValue(defaultValue = "XLSX") String fileExtension) {
        String downloadErrorMessage = "An error occurred while generating the download file. Contact the development team at bidevteam@cornell.edu.";
        try {
            FileType extension = Enum.valueOf(FileType.class, fileExtension);
            DownloadFile germplasmListFile = germplasmService.exportGermplasm(programId, extension);
            HttpResponse<StreamedFile> germplasmExport = HttpResponse.ok(germplasmListFile.getStreamedFile()).header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename="+germplasmListFile.getFileName()+extension.getExtension());
            return germplasmExport;
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            HttpResponse response = HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, downloadErrorMessage).contentType(MediaType.TEXT_PLAIN).body(downloadErrorMessage);
            return response;
        }
    }

    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/germplasm/{germplasmId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
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

    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/germplasm/{germplasmId}/pedigree{?notation}{?includeSiblings}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<BrAPIGermplasmPedigreeResponse> getGermplasmPedigreeInfo(
            @PathVariable("programId") UUID programId,
            @PathVariable("germplasmId") String germplasmId,
            @QueryValue(defaultValue = "") String notation,
            @QueryValue(defaultValue = "false") Boolean includeSiblings)
    {
        try {
            log.debug("fetching pedigree for germ id:" +  germplasmId +" for program: " + programId);
            BrAPIPedigreeNode returnNode;
            BrAPIGermplasmPedigreeResponse response;
            BrAPIMetadata metadata;
            if (germplasmId.endsWith("-Unknown")) {
                //Unknown germplasm node
                returnNode = new BrAPIPedigreeNode();
                returnNode.setGermplasmDbId(germplasmId);
                returnNode.setGermplasmName("Unknown");

                BrAPIPedigreeNodeParents emptyParents = new BrAPIPedigreeNodeParents();
                returnNode.addParentsItem(emptyParents);

                returnNode.setSiblings(new ArrayList<>());
                returnNode.setPedigree("/");
                metadata = new BrAPIMetadata();
                BrAPIStatus status = new BrAPIStatus();
                status.setMessage("Complete");
                status.setMessageType(BrAPIStatus.MessageTypeEnum.INFO);
                BrAPIIndexPagination pagination = new BrAPIIndexPagination();
                pagination.setTotalPages(1);
                pagination.setTotalCount(1);
                pagination.setPageSize(1);
                pagination.setCurrentPage(0);
                metadata.setStatus(Collections.singletonList(status));
                metadata.setPagination(pagination);
                response = new BrAPIGermplasmPedigreeResponse();
            } else {
                BrAPIGermplasm germplasm = germplasmService.getGermplasmByDBID(programId, germplasmId)
                                                                             .orElseThrow(() -> new DoesNotExistException("DBID for this germplasm does not exist"));

                //Forward the pedigree call to the backing BrAPI system of the program passing the germplasmDbId that came in the request
                GermplasmApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), GermplasmApi.class);
                ApiResponse<BrAPIGermplasmPedigreeResponse> pedigreeResponse = api.germplasmGermplasmDbIdPedigreeGet(germplasmId, notation, includeSiblings);
                returnNode = pedigreeResponse.getBody().getResult();
                metadata = pedigreeResponse.getBody().getMetadata();
                response = pedigreeResponse.getBody();

                //Add nodes for unknown parents if applicable
                if (germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN) && germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.FEMALE_PARENT_UNKNOWN).getAsBoolean()) {
                    BrAPIPedigreeNodeParents unknownFemale = new BrAPIPedigreeNodeParents();
                    unknownFemale.setGermplasmDbId(germplasm.getGermplasmDbId()+"-F-Unknown");
                    unknownFemale.setGermplasmName("Unknown");
                    unknownFemale.setParentType(BrAPIParentType.FEMALE);
                    returnNode.addParentsItem(unknownFemale);
                }
                if (germplasm.getAdditionalInfo().has(BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN) && germplasm.getAdditionalInfo().get(BrAPIAdditionalInfoFields.MALE_PARENT_UNKNOWN).getAsBoolean()) {
                    BrAPIPedigreeNodeParents unknownMale = new BrAPIPedigreeNodeParents();
                    unknownMale.setGermplasmDbId(germplasm.getGermplasmDbId()+"-M-Unknown");
                    unknownMale.setGermplasmName("Unknown");
                    unknownMale.setParentType(BrAPIParentType.MALE);
                    returnNode.addParentsItem(unknownMale);
                }

                //If no parents, need to add empty parents for display to work
                if (returnNode.getParents().isEmpty()) {
                    BrAPIPedigreeNodeParents emptyParents = new BrAPIPedigreeNodeParents();
                    returnNode.addParentsItem(emptyParents);
                }
            }
            response.setResult(returnNode);
            response.setMetadata(metadata);
            return HttpResponse.ok(response);
        } catch (InternalServerException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pedigree node");
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, "Pedigree node not found");
        } catch (ApiException e) {
            log.info(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pedigree node");
        }
    }

    @Get("/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/germplasm/{germplasmId}/progeny")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<BrAPIGermplasmProgenyResponse> getGermplasmProgenyInfo(
            @PathVariable("programId") UUID programId,
            @PathVariable("germplasmId") String germplasmId) {
        try {
            log.debug("fetching progeny for germ id:" +  germplasmId +" for program: " + programId);
            BrAPIProgenyNode returnNode;
            BrAPIGermplasmProgenyResponse response;
            BrAPIMetadata metadata;
            if (germplasmId.endsWith("-Unknown")) {
                //Unknown germplasm node, only has one child
                //We know progeny and parent type based on germplasmId
                returnNode = new BrAPIProgenyNode();
                returnNode.setGermplasmDbId(germplasmId);
                returnNode.setGermplasmName("Unknown [-0]");
                ArrayList<BrAPIProgenyNodeProgeny> progeny = new ArrayList<>();
                BrAPIProgenyNodeProgeny singleProgeny = new BrAPIProgenyNodeProgeny();
                singleProgeny.setGermplasmDbId(germplasmId.split("-[FM]-Unknown")[0]);
                singleProgeny.setGermplasmName("Name"); //does not seem necessary, preferable to avoid longer id string/making more endpoint calls
                if (germplasmId.endsWith("F-Unknown")) {
                    singleProgeny.setParentType(BrAPIParentType.FEMALE);
                } else {
                    singleProgeny.setParentType(BrAPIParentType.MALE);
                }
                returnNode.setProgeny(Collections.singletonList(singleProgeny));

                metadata = new BrAPIMetadata();
                BrAPIStatus status = new BrAPIStatus();
                status.setMessage("Complete");
                status.setMessageType(BrAPIStatus.MessageTypeEnum.INFO);
                BrAPIIndexPagination pagination = new BrAPIIndexPagination();
                pagination.setTotalPages(1);
                pagination.setTotalCount(1);
                pagination.setPageSize(1);
                pagination.setCurrentPage(0);
                metadata.setStatus(Collections.singletonList(status));
                metadata.setPagination(pagination);
                response = new BrAPIGermplasmProgenyResponse();
                response.setResult(returnNode);
                response.setMetadata(metadata);
                return HttpResponse.ok(response);
            } else {
                //Forward the progeny call to the backing BrAPI system of the program passing the germplasmDbId that came in the request
                GermplasmApi api = brAPIEndpointProvider.get(programDAO.getCoreClient(programId), GermplasmApi.class);
                ApiResponse<BrAPIGermplasmProgenyResponse> progenyResponse = api.germplasmGermplasmDbIdProgenyGet(germplasmId);

                //If no progeny, need to add empty progeny for display to work
                if (progenyResponse.getBody().getResult().getProgeny().isEmpty()) {
                    BrAPIProgenyNodeProgeny emptyProgeny = new BrAPIProgenyNodeProgeny();
                    progenyResponse.getBody().getResult().addProgenyItem(emptyProgeny);
                }
                return HttpResponse.ok(progenyResponse.getBody());
            }
        } catch (InternalServerException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pedigree node");
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pedigree node");
        }
    }

    @Get("/programs/{programId}/germplasm/{germplasmId}/genotype")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES})
    public HttpResponse<Response<GermplasmGenotype>> getGermplasmGenotype(@PathVariable("programId") UUID programId,
                                                                          @PathVariable("germplasmId") String germplasmId) {

        try {
            BrAPIGermplasm germplasm = germplasmDAO.getGermplasmByUUID(germplasmId, programId);
            GermplasmGenotype germplasmGenotype = genoService.retrieveGenotypeData(programId, germplasm);

            Response<GermplasmGenotype> response = new Response(germplasmGenotype);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.NOT_FOUND, "Germplasm not found");
        } catch (AuthorizationException | ApiException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving genotype data");
        }
    }

}
