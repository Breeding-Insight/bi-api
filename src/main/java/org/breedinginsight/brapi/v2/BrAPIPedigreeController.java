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

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIIndexPagination;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.germ.BrAPIPedigreeNode;
import org.brapi.v2.model.germ.response.BrAPIPedigreeListResponse;
import org.brapi.v2.model.germ.response.BrAPIPedigreeListResponseResult;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.dao.BrAPIPedigreeDAO;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.utilities.Utilities;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIPedigreeController {
    private final String referenceSource;

    private final BrAPIPedigreeDAO pedigreeDAO;

    private final ProgramService programService;

    @Inject
    public BrAPIPedigreeController(@Property(name = "brapi.server.reference-source") String referenceSource, BrAPIPedigreeDAO pedigreeDAO, ProgramService programService) {
        this.referenceSource = referenceSource;
        this.pedigreeDAO = pedigreeDAO;
        this.programService = programService;
    }

    @Get("/pedigree")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<BrAPIPedigreeListResponse> pedigreeGet(@PathVariable("programId") UUID programId,
                                                               @Nullable @QueryValue("accessionNumber") String accessionNumber,
                                                               @Nullable @QueryValue("collection") String collection,
                                                               @Nullable @QueryValue("familyCode") String familyCode,
                                                               @Nullable @QueryValue("binomialName") String binomialName,
                                                               @Nullable @QueryValue("genus") Boolean genus,
                                                               @Nullable @QueryValue("species") String species,
                                                               @Nullable @QueryValue("synonym") String synonym,
                                                               @Nullable @QueryValue("includeParents") Boolean includeParents,
                                                               @Nullable @QueryValue("includeSiblings") Boolean includeSiblings,
                                                               @Nullable @QueryValue("includeProgeny") Boolean includeProgeny,
                                                               @Nullable @QueryValue("includeFullTree") Boolean includeFullTree,
                                                               @Nullable @QueryValue("pedigreeDepth") Integer pedigreeDepth,
                                                               @Nullable @QueryValue("progenyDepth") Integer progenyDepth,
                                                               @Nullable @QueryValue("commonCropName") String commonCropName,
                                                               @Nullable @QueryValue("programDbId") String programDbId,
                                                               @Nullable @QueryValue("trialDbId") String trialDbId,
                                                               @Nullable @QueryValue("studyDbId") String studyDbId,
                                                               @Nullable @QueryValue("germplasmDbId") String germplasmDbId,
                                                               @Nullable @QueryValue("germplasmName") String germplasmName,
                                                               @Nullable @QueryValue("germplasmPUI") String germplasmPUI,
                                                               @Nullable @QueryValue("externalReferenceId") String externalReferenceId,
                                                               @Nullable @QueryValue("externalReferenceSource") String externalReferenceSource,
                                                               @Nullable @QueryValue("page") Integer page,
                                                               @Nullable @QueryValue("pageSize") Integer pageSize) {

        log.debug("pedigreeGet: fetching pedigree by filters");

        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.warn("Program id: " + programId + " not found");
            return HttpResponse.notFound();
        }

        try {
            List<BrAPIPedigreeNode> pedigree = pedigreeDAO.getPedigree(program.get());

            return HttpResponse.ok(
                    new BrAPIPedigreeListResponse()
                            .metadata(new BrAPIMetadata().pagination(new BrAPIIndexPagination().currentPage(0)
                                    .totalPages(1)
                                    .pageSize(pedigree.size())
                                    .totalCount(pedigree.size())))
                            .result(new BrAPIPedigreeListResponseResult().data(pedigree))
            );
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "error fetching pedigree");
        }
    }

    @Post("/pedigree")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> pedigreePost(@PathVariable("programId") UUID programId, @Body List<BrAPIPedigreeNode> body) {
        //DO NOT IMPLEMENT - Users are only able to create pedigree via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Put("/pedigree")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> pedigreePut(@PathVariable("programId") UUID programId, @Body Map<String, BrAPIPedigreeNode> body) {
        //DO NOT IMPLEMENT - Users aren't yet able to update observation units
        return HttpResponse.notFound();
    }

    // TODO: search and retrieve endpoints

}
