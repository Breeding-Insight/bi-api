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
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.Role;
import org.breedinginsight.model.SystemRole;
import org.breedinginsight.services.RoleService;
import org.breedinginsight.services.SystemRoleService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class RoleController {

    @Inject
    private RoleService roleService;
    @Inject
    private SystemRoleService systemRoleService;

    @Get("programs/roles")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<Role>>> getRoles() {

        List<Role> roles = roleService.getAll();

        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        //TODO: Put in the actual page size
        Pagination pagination = new Pagination(roles.size(), 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<Role>> response = new Response(metadata, new DataResponse<>(roles));
        return HttpResponse.ok(response);
    }

    @Get("programs/roles/{roleId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<Role>> getRole(@PathVariable UUID roleId) {

        Optional<Role> role = roleService.getById(roleId);
        if(role.isPresent()) {
            Response<Role> response = new Response(role.get());
            return HttpResponse.ok(response);
        } else {
            return HttpResponse.notFound();
        }
    }

    @Get("/roles")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<SystemRole>>> getSystemRoles() {

        List<SystemRole> roles = systemRoleService.getAll();

        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        //TODO: Put in the actual page size
        Pagination pagination = new Pagination(roles.size(), 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<SystemRole>> response = new Response(metadata, new DataResponse<>(roles));
        return HttpResponse.ok(response);
    }

    @Get("/roles/{roleId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<SystemRole>> getSystemRole(@PathVariable UUID roleId) {

        Optional<SystemRole> role = systemRoleService.getById(roleId);
        if(role.isPresent()) {
            Response<SystemRole> response = new Response(role.get());
            return HttpResponse.ok(response);
        } else {
            return HttpResponse.notFound();
        }
    }
}
