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
import org.breedinginsight.model.EnvironmentType;
import org.breedinginsight.services.EnvironmentTypeService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class EnvironmentTypeController {

    @Inject
    private EnvironmentTypeService environmentTypeService;

    @Get("/environment-data-types")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<DataResponse<EnvironmentType>>> getEnvironmentTypes() {
        List<EnvironmentType> environmentTypes = environmentTypeService.getAll();

        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        //TODO: Put in the actual page size
        Pagination pagination = new Pagination(environmentTypes.size(), 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<EnvironmentType>> response = new Response(metadata, new DataResponse<>(environmentTypes));
        return HttpResponse.ok(response);
    }

    @Get("/environment-data-types/{environmentTypeId}")
    @Produces(MediaType.APPLICATION_JSON)
    @AddMetadata
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<Response<EnvironmentType>> getEnvironmentType(@PathVariable UUID environmentTypeId) {
        Optional<EnvironmentType> environmentType = environmentTypeService.getById(environmentTypeId);
        if(environmentType.isPresent()) {
            Response<EnvironmentType> response = new Response(environmentType.get());
            return HttpResponse.ok(response);
        } else {
            return HttpResponse.notFound();
        }
    }
}
