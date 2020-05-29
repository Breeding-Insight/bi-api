package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.daos.TraitDAO;
import org.breedinginsight.model.User;

import javax.inject.Inject;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class TraitController {

    @Inject
    TraitDAO traitDAO;

    @Get("programs/{programId}/traits")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @BrAPIService
    @Secured({SecurityRule.IS_AUTHENTICATED})
    public HttpResponse<Response<User>> getTraits(@PathVariable UUID programId) {

        traitDAO.getTraitsFull();
        return HttpResponse.ok();
    }
}
