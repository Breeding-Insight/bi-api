package org.breedinginsight.api.v1.controller.geno;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRole;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.geno.GenoService;

import javax.inject.Inject;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class GenoDataUploadController {
    private final GenoService genoService;
    private final SecurityService securityService;

    @Inject
    public GenoDataUploadController(GenoService genoService, SecurityService securityService) {
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
            ImportResponse result = genoService.submitGenoData(actingUser.getId(), programId, experimentId, upload);
            Response<ImportResponse> response = new Response<>(result);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            return HttpResponse.unauthorized();
        }
    }
}
