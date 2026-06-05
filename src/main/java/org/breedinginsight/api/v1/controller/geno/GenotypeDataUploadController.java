package org.breedinginsight.api.v1.controller.geno;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.api.auth.*;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.model.GenotypeImportDetails;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.geno.GenotypeService;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.GenotypeImportQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class GenotypeDataUploadController {
    private final GenotypeService genoService;
    private final SecurityService securityService;
    private final ProgramService programService;
    private final GenotypeImportQueryMapper genotypeImportQueryMapper;

    @Inject
    public GenotypeDataUploadController(GenotypeService genoService, SecurityService securityService,
                                        ProgramService programService, GenotypeImportQueryMapper genotypeImportQueryMapper) {
        this.genoService = genoService;
        this.securityService = securityService;
        this.programService = programService;
        this.genotypeImportQueryMapper = genotypeImportQueryMapper;
    }

    @Get("programs/{programId}/geno/imports{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = ProgramSecuredRoleGroup.PROGRAM_SCOPED_ROLES)
    public HttpResponse<Response<DataResponse<GenotypeImportDetails>>> getGenotypeImports(
            @PathVariable UUID programId,
            @QueryValue @QueryValid(using = GenotypeImportQueryMapper.class) @Valid QueryParams queryParams) {
        Optional<Program> program = programService.getById(programId);
        if (program.isEmpty()) {
            log.info("programId not found: {}", programId.toString());
            return HttpResponse.notFound();
        }

        return ResponseUtils.getQueryResponse(genoService.getGenotypeImports(programId), genotypeImportQueryMapper, queryParams);
    }

    @Post("programs/{programId}/submissions/{submissionId}/geno/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.PROGRAM_ADMIN})
    public HttpResponse<Response<ImportResponse>> uploadData(@PathVariable UUID programId, @PathVariable UUID submissionId, @Part("file") CompletedFileUpload upload) {
        AuthenticatedUser actingUser = securityService.getUser();
        try {
            ImportResponse result = genoService.submitGenotypeData(actingUser.getId(), programId, submissionId, upload);
            Response<ImportResponse> response = new Response<>(result);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.error("Missing data", e);
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.error("Error authorizing to backing service", e);
            return HttpResponse.unauthorized();
        } catch (ApiException e) {
            log.error("Error importing geno data", e);
            return HttpResponse.serverError();
        }
    }
}
