/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.api.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.request.OrcidRequest;
import org.breedinginsight.api.model.v1.request.SystemRolesRequest;
import org.breedinginsight.api.model.v1.request.UserRequest;
import org.breedinginsight.api.model.v1.request.query.QueryParams;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.api.model.v1.validators.SearchValid;
import org.breedinginsight.api.v1.controller.metadata.AddMetadata;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.UserService;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.AuthorizationException;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.UserQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class UserController {

    private UserService userService;
    private SecurityService securityService;
    private UserQueryMapper userQueryMapper;

    @Inject
    public UserController(UserService userService, SecurityService securityService,
                          UserQueryMapper userQueryMapper) {
        this.userService = userService;
        this.securityService = securityService;
        this.userQueryMapper = userQueryMapper;
    }

    @Get("/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured({SecurityRule.IS_AUTHENTICATED})
    public HttpResponse<Response<User>> userinfo() {

        AuthenticatedUser actingUser = securityService.getUser();
        Optional<User> user = userService.getById(actingUser.getId());

        if (user.isPresent()) {
            Response<User> response = new Response<>(user.get());
            return HttpResponse.ok(response);
        } else {
            return HttpResponse.unauthorized();
        }
    }

    @Get("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> users(@PathVariable UUID userId) {

        Optional<User> user = userService.getById(userId);

        if(user.isPresent()) {
            Response<User> response = new Response(user.get());
            return HttpResponse.ok(response);
        } else {
            log.info("User not found");
            return HttpResponse.notFound();
        }
    }

    @Get("/users{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<User>>> users(
            @QueryValue @QueryValid(using = UserQueryMapper.class) @Valid QueryParams queryParams
    ) {

        List<User> users = userService.getAll();
        return ResponseUtils.getQueryResponse(users, userQueryMapper, queryParams);
    }

    @Post("/users/search{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<Response<DataResponse<Program>>> postUsersSearch(
            @QueryValue @QueryValid(using = UserQueryMapper.class) @Valid QueryParams queryParams,
            @Body @SearchValid(using = UserQueryMapper.class) SearchRequest searchRequest) {

        List<User> users = userService.getAll();
        return ResponseUtils.getQueryResponse(users, userQueryMapper, searchRequest, queryParams);
    }

    @Post("/users")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured({"ADMIN"})
    public HttpResponse<Response<User>> createUser(@Body @Valid UserRequest requestUser){

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            User user = userService.create(actingUser, requestUser);
            Response<User> response = new Response<>(user);
            return HttpResponse.ok(response);
        } catch (AlreadyExistsException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        } catch (UnprocessableEntityException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Put("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> updateUser(@PathVariable UUID userId, @Body @Valid UserRequest requestUser){

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            User user = userService.update(actingUser, userId, requestUser);
            Response<User> response = new Response<>(user);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AlreadyExistsException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @Delete("/users/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured({"ADMIN"})
    public HttpResponse archiveUser(@PathVariable UUID userId){

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            userService.archive(actingUser, userId);
            return HttpResponse.ok();
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AuthorizationException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @Put("users/{userId}/roles")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured({"ADMIN"})
    public HttpResponse<Response<User>> updateUserSystemRoles(@PathVariable UUID userId, @Body @Valid SystemRolesRequest requestUser) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            User user = userService.updateRoles(actingUser, userId, requestUser);
            Response<User> response = new Response<>(user);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (UnprocessableEntityException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (AuthorizationException e) {
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    //TODO: Remove once registration flow is implemented.
    // Endpoint not in the api docs. This will be removed in v0.2
    @Put("/users/{userId}/orcid")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<User>> updateUser(@PathVariable UUID userId, @Body @Valid OrcidRequest orcidRequest) {

        try {
            AuthenticatedUser actingUser = securityService.getUser();
            User user = userService.updateOrcid(actingUser, userId, orcidRequest);
            Response<User> response = new Response<>(user);
            return HttpResponse.ok(response);
        } catch (DoesNotExistException e) {
            log.info(e.getMessage());
            return HttpResponse.notFound();
        } catch (AlreadyExistsException e){
            log.info(e.getMessage());
            return HttpResponse.status(HttpStatus.CONFLICT, e.getMessage());
        }
    }

}
