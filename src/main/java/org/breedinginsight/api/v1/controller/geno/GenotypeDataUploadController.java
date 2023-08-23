package org.breedinginsight.api.v1.controller.geno;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRole;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.geno.GenotypeService;

import javax.inject.Inject;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class GenotypeDataUploadController {
    private final GenotypeService genoService;
    private final SecurityService securityService;

    @Inject
    public GenotypeDataUploadController(GenotypeService genoService, SecurityService securityService) {
        this.genoService = genoService;
        this.securityService = securityService;
    }

    @Post("programs/{programId}/experiments/{experimentId}/geno/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @ProgramSecured(roles = {ProgramSecuredRole.BREEDER})
    public HttpResponse<Response<ImportResponse>> uploadData(@PathVariable UUID programId, @PathVariable UUID experimentId, @Part("file") CompletedFileUpload upload) {
        AuthenticatedUser actingUser = securityService.getUser();
        try {
            ImportResponse result = genoService.submitGenotypeData(actingUser.getId(), programId, experimentId, upload);
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
