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
package org.breedinginsight.brapi.v1.controller;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.api.model.v1.request.query.FilterRequest;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.brapi.v1.model.ObservationVariable;
import org.breedinginsight.brapi.v1.model.request.query.ObservationVariablesQuery;
import org.breedinginsight.brapi.v1.model.response.mappers.ObservationVariableQueryMapper;
import org.breedinginsight.brapi.v1.services.BrapiObservationVariableService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.response.ResponseUtils;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller("/${micronaut.brapi.v1}")
public class BrapiObservationVariablesController {

    private BrapiObservationVariableService variableService;
    private SecurityService securityService;
    private ObservationVariableQueryMapper variableQueryMapper;

    @Inject
    public BrapiObservationVariablesController(BrapiObservationVariableService variableService,
                                               SecurityService securityService,
                                               ObservationVariableQueryMapper variableQueryMapper){
        this.variableService = variableService;
        this.securityService = securityService;
        this.variableQueryMapper = variableQueryMapper;
    }

    @Get("/variables{?variablesQuery*}")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<ObservationVariable>>> getVariables(
            @QueryValue @QueryValid(using = ObservationVariableQueryMapper.class) @Valid ObservationVariablesQuery variablesQuery) {

        AuthenticatedUser actingUser = securityService.getUser();
        List <ObservationVariable> variables = new ArrayList<>();

        try {
            variables = variableService.getBrapiObservationVariablesForUser(actingUser);
        } catch (DoesNotExistException e) {
            log.error(e.getMessage());
            return HttpResponse.notFound();
        }

        List<FilterRequest> filters = new ArrayList<>();
        if (variablesQuery.getObservationVariableDbId() != null) {
            filters.add(FilterRequest.builder()
                    .field("observationVariableDbId")
                    .value(variablesQuery.getObservationVariableDbId())
                    .build());
        }
        if (variablesQuery.getTraitClass() != null) {
            filters.add(FilterRequest.builder()
                    .field("traitClass")
                    .value(variablesQuery.getTraitClass())
                    .build());
        }
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setFilters(filters);

        return ResponseUtils.getBrapiQueryResponse(variables, variableQueryMapper, variablesQuery, searchRequest);
    }



}
