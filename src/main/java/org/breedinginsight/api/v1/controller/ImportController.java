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
import org.breedinginsight.model.brapi_import.BrAPIImportConfigManager;
import org.breedinginsight.model.brapi_import.imports.GermplasmImport;
import org.breedinginsight.model.brapi_import.response.ImportConfig;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class ImportController {

    @Inject
    BrAPIImportConfigManager importManager;

    @Get("/import/types")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    @AddMetadata
    public HttpResponse<Response<DataResponse<ImportConfig>>> getImportTypes() {
        List<ImportConfig> configs = importManager.getAllTypeConfigs();

        //TODO: Add actual page size
        List<Status> metadataStatus = new ArrayList<>();
        metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
        Pagination pagination = new Pagination(1, 1, 1, 0);
        Metadata metadata = new Metadata(pagination, metadataStatus);

        Response<DataResponse<ImportConfig>> response = new Response(metadata, new DataResponse<>(configs));
        return HttpResponse.ok(response);
    }

}
