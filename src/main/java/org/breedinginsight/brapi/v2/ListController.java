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

package org.breedinginsight.brapi.v2;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.request.query.SearchRequest;
import org.breedinginsight.api.model.v1.validators.QueryValid;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.model.request.query.ListQuery;
import org.breedinginsight.brapi.v2.services.BrAPIGermplasmService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.response.ResponseUtils;
import org.breedinginsight.utilities.response.mappers.ListQueryMapper;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ListController {

    private final BrAPIGermplasmService germplasmService;
    private final ListQueryMapper listQueryMapper;

    @Inject
    public ListController(BrAPIGermplasmService germplasmService,
                          ListQueryMapper listQueryMapper) {
        this.germplasmService = germplasmService;
        this.listQueryMapper = listQueryMapper;
    }


    //@Get(BrapiVersion.BRAPI_V2 + "/lists")
    @Get("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/lists{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse getLists(@PathVariable("programId") UUID programId, HttpRequest<String> request,
                                 @QueryValue @QueryValid(using = ListQueryMapper.class) @Valid ListQuery queryParams
    ) throws DoesNotExistException, ApiException {
        try {
            List<BrAPIListSummary> brapiLists;

            // If the date display format was sent as a query param, then update the query mapper.
            String dateFormatParam = queryParams.getDateDisplayFormat();
            if (dateFormatParam != null) {
                listQueryMapper.setDateDisplayFormat(dateFormatParam);
            }

            if (queryParams.getListType() == null) {
                // TODO: in future return all list types but for now just return germplasm
                brapiLists = germplasmService.getGermplasmListsByProgramId(programId, request);
            } else {
                // TODO: return appropriate lists by type, only germplasm currently
                switch (queryParams.getListType()) {
                    case "germplasm":
                    default:
                        brapiLists = germplasmService.getGermplasmListsByProgramId(programId, request);
                }
            }

            SearchRequest searchRequest = queryParams.constructSearchRequest();
            return ResponseUtils.getBrapiQueryResponse(brapiLists, listQueryMapper, queryParams, searchRequest);

        } catch (IllegalArgumentException e) {
            log.info(e.getMessage(), e);
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY, "Error parsing requested date format");
        }
    }
}
