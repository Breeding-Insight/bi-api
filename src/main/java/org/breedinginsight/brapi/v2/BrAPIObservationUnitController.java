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
import io.micronaut.http.annotation.*;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIIndexPagination;
import org.brapi.v2.model.BrAPIMetadata;
import org.brapi.v2.model.BrAPIStatus;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitListResponseResult;
import org.brapi.v2.model.pheno.response.BrAPIObservationUnitSingleResponse;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.utilities.Utilities;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2)
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIObservationUnitController {
    private final String referenceSource;

    private final BrAPIObservationUnitDAO observationUnitDAO;

    private final ProgramService programService;

    @Inject
    public BrAPIObservationUnitController(@Property(name = "brapi.server.reference-source") String referenceSource, BrAPIObservationUnitDAO observationUnitDAO, ProgramService programService) {
        this.referenceSource = referenceSource;
        this.observationUnitDAO = observationUnitDAO;
        this.programService = programService;
    }

    @Get("/observationunits")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<BrAPIObservationUnitListResponse> observationunitsGet(@PathVariable("programId") UUID programId,
                                               @Nullable @QueryValue("observationUnitDbId") String observationUnitDbId,
                                               @Nullable @QueryValue("observationUnitName") String observationUnitName,
                                               @Nullable @QueryValue("locationDbId") String locationDbId,
                                               @Nullable @QueryValue("seasonDbId") String seasonDbId,
                                               @Nullable @QueryValue("includeObservations") Boolean includeObservations,
                                               @Nullable @QueryValue("observationUnitLevelName") String observationUnitLevelName,
                                               @Nullable @QueryValue("observationUnitLevelOrder") String observationUnitLevelOrder,
                                               @Nullable @QueryValue("observationUnitLevelCode") String observationUnitLevelCode,
                                               @Nullable @QueryValue("observationUnitLevelRelationshipName") String observationUnitLevelRelationshipName,
                                               @Nullable @QueryValue("observationUnitLevelRelationshipOrder") String observationUnitLevelRelationshipOrder,
                                               @Nullable @QueryValue("observationUnitLevelRelationshipCode") String observationUnitLevelRelationshipCode,
                                               @Nullable @QueryValue("observationUnitLevelRelationshipDbId") String observationUnitLevelRelationshipDbId,
                                               @Nullable @QueryValue("commonCropName") String commonCropName,
                                               @Nullable @QueryValue("programDbId") String programDbId,
                                               @Nullable @QueryValue("trialDbId") String trialDbId,
                                               @Nullable @QueryValue("studyDbId") String studyDbId,
                                               @Nullable @QueryValue("germplasmDbId") String germplasmDbId,
                                               @Nullable @QueryValue("externalReferenceID") String externalReferenceID,
                                               @Nullable @QueryValue("externalReferenceId") String externalReferenceId,
                                               @Nullable @QueryValue("externalReferenceSource") String externalReferenceSource,
                                               @Nullable @QueryValue("page") Integer page,
                                               @Nullable @QueryValue("pageSize") Integer pageSize) {

        log.debug("observationunitsGet: fetching ous by filters");

        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.warn("Program id: " + programId + " not found");
            return HttpResponse.notFound();
        }

        try {
            Optional<Integer> levelOrder = Optional.empty();
            Optional<Integer> levelRelationshipOrder = Optional.empty();
            if(observationUnitLevelOrder != null) {
                levelOrder = Optional.of(Integer.parseInt(observationUnitLevelOrder));
            }
            if(observationUnitLevelRelationshipOrder != null) {
                levelOrder = Optional.of(Integer.parseInt(observationUnitLevelRelationshipOrder));
            }

            List<BrAPIObservationUnit> observationUnits = observationUnitDAO.getObservationUnits(program.get(),
                                                                                                 Optional.ofNullable(observationUnitDbId),
                                                                                                 Optional.ofNullable(observationUnitName),
                                                                                                 Optional.ofNullable(locationDbId),
                                                                                                 Optional.ofNullable(seasonDbId),
                                                                                                 Optional.ofNullable(includeObservations),
                                                                                                 Optional.ofNullable(observationUnitLevelName),
                                                                                                 levelOrder,
                                                                                                 Optional.ofNullable(observationUnitLevelCode),
                                                                                                 Optional.ofNullable(observationUnitLevelRelationshipName),
                                                                                                 levelRelationshipOrder,
                                                                                                 Optional.ofNullable(observationUnitLevelRelationshipCode),
                                                                                                 Optional.ofNullable(observationUnitLevelRelationshipDbId),
                                                                                                 Optional.ofNullable(commonCropName),
                                                                                                 Optional.ofNullable(trialDbId),
                                                                                                 Optional.ofNullable(studyDbId),
                                                                                                 Optional.ofNullable(germplasmDbId))
                                                                            .stream()
                                                                            .peek(this::setDbIds)
                                                                            .collect(Collectors.toList());

            return HttpResponse.ok(new BrAPIObservationUnitListResponse().metadata(new BrAPIMetadata().pagination(new BrAPIIndexPagination().currentPage(0)
                                                                                                                                            .totalPages(1)
                                                                                                                                            .pageSize(observationUnits.size())
                                                                                                                                            .totalCount(observationUnits.size())))
                                                                         .result(new BrAPIObservationUnitListResponseResult().data(observationUnits)));
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError(new BrAPIObservationUnitListResponse().metadata(new BrAPIMetadata().status(List.of(new BrAPIStatus().messageType(BrAPIStatus.MessageTypeEnum.ERROR)
                                                                                                                                                .message("Error fetching observation units")))));
        } catch (Exception e) {
            log.error("Error fetching OUs", e);
            return HttpResponse.serverError(new BrAPIObservationUnitListResponse().metadata(new BrAPIMetadata().status(List.of(new BrAPIStatus().messageType(BrAPIStatus.MessageTypeEnum.ERROR)
                                                                                                                                                .message("Error fetching observation units")))));
        }
    }

    @Get("/observationunits/{observationUnitDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<BrAPIObservationUnitSingleResponse> observationunitsObservationUnitDbIdGet(@PathVariable("programId") UUID programId, @PathVariable("observationUnitDbId") String observationUnitDbId) {
        log.debug("observationunitsObservationUnitDbIdGet: fetching ou by externalReferenceId: " + observationUnitDbId);
        Optional<Program> program = programService.getById(programId);
        if(program.isEmpty()) {
            log.warn("Program id: " + programId + " not found");
            return HttpResponse.notFound();
        }
        try {
            List<BrAPIObservationUnit> ous = observationUnitDAO.getObservationUnitsById(List.of(observationUnitDbId), program.get());
            if(ous.size() != 1) {
                log.warn("did not find a single ou with externalReferenceId: " + observationUnitDbId);
                return HttpResponse.notFound();
            }
            setDbIds(ous.get(0));
            return HttpResponse.ok(new BrAPIObservationUnitSingleResponse().result(ous.get(0)));
        } catch (ApiException e) {
            log.error(Utilities.generateApiExceptionLogMessage(e), e);
            return HttpResponse.serverError(new BrAPIObservationUnitSingleResponse().metadata(new BrAPIMetadata().status(List.of(new BrAPIStatus().messageType(BrAPIStatus.MessageTypeEnum.ERROR)
                                                                                                                                                .message("Error fetching observation unit")))));
        } catch (Exception e) {
            log.error("Error fetching OU", e);
            return HttpResponse.serverError(new BrAPIObservationUnitSingleResponse().metadata(new BrAPIMetadata().status(List.of(new BrAPIStatus().messageType(BrAPIStatus.MessageTypeEnum.ERROR)
                                                                                                                                                .message("Error fetching observation unit")))));
        }
    }

    @Put("/observationunits/{observationUnitDbId}")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> observationunitsObservationUnitDbIdPut(@PathVariable("programId") UUID programId, @PathVariable("observationUnitDbId") String observationUnitDbId, @Body BrAPIObservationUnit body) {
        //DO NOT IMPLEMENT - Users aren't yet able to update observation units
        return HttpResponse.notFound();
    }

    @Post("/observationunits")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> observationunitsPost(@PathVariable("programId") UUID programId, @Body List<BrAPIObservationUnit> body) {
        //DO NOT IMPLEMENT - Users are only able to create observation units via the DeltaBreed UI
        return HttpResponse.notFound();
    }

    @Put("/observationunits")
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> observationunitsPut(@PathVariable("programId") UUID programId, @Body Map<String, BrAPIObservationUnit> body) {
        //DO NOT IMPLEMENT - Users aren't yet able to update observation units
        return HttpResponse.notFound();
    }

    @Get("/observationunits/table")
    @Produces({"application/json", "text/csv", "text/tsv"})
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> observationunitsTableGet(@PathVariable("programId") UUID programId,
                                                    @Nullable @Header("Accept") String accept,
                                                    @Nullable @QueryValue("observationUnitDbId") String observationUnitDbId,
                                                    @Nullable @QueryValue("observationVariableDbId") String observationVariableDbId,
                                                    @Nullable @QueryValue("locationDbId") String locationDbId,
                                                    @Nullable @QueryValue("seasonDbId") String seasonDbId,
                                                    @Nullable @QueryValue("observationLevel") String observationLevel,
                                                    @Nullable @QueryValue("programDbId") String programDbId,
                                                    @Nullable @QueryValue("trialDbId") String trialDbId,
                                                    @Nullable @QueryValue("studyDbId") String studyDbId,
                                                    @Nullable @QueryValue("germplasmDbId") String germplasmDbId,
                                                    @Nullable @QueryValue("observationUnitLevelName") String observationUnitLevelName,
                                                    @Nullable @QueryValue("observationUnitLevelOrder") String observationUnitLevelOrder,
                                                    @Nullable @QueryValue("observationUnitLevelCode") String observationUnitLevelCode,
                                                    @Nullable @QueryValue("observationUnitLevelRelationshipName") String observationUnitLevelRelationshipName,
                                                    @Nullable @QueryValue("observationUnitLevelRelationshipOrder") String observationUnitLevelRelationshipOrder,
                                                    @Nullable @QueryValue("observationUnitLevelRelationshipCode") String observationUnitLevelRelationshipCode,
                                                    @Nullable @QueryValue("observationUnitLevelRelationshipDbId") String observationUnitLevelRelationshipDbId) {
        return HttpResponse.notFound();
    }

    private void setDbIds(BrAPIObservationUnit ou) {
        ou.observationUnitDbId(Utilities.getExternalReference(ou.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.OBSERVATION_UNITS))
                              .orElseThrow(() -> new IllegalStateException("No BI external reference found"))
                              .getReferenceID());
        ou.studyDbId(Utilities.getExternalReference(ou.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.STUDIES))
                              .orElseThrow(() -> new IllegalStateException("No BI external reference found"))
                              .getReferenceID());
        ou.trialDbId(Utilities.getExternalReference(ou.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.TRIALS))
                                 .orElseThrow(() -> new IllegalStateException("No BI external reference found"))
                                 .getReferenceID());
        ou.programDbId(Utilities.getExternalReference(ou.getExternalReferences(), Utilities.generateReferenceSource(referenceSource, ExternalReferenceSource.PROGRAMS))
                                   .orElseThrow(() -> new IllegalStateException("No BI external reference found"))
                                   .getReferenceID());
        if (ou.getAdditionalInfo().has(BrAPIAdditionalInfoFields.GERMPLASM_UUID)) {
            ou.setGermplasmDbId(ou.getAdditionalInfo()
                                  .get(BrAPIAdditionalInfoFields.GERMPLASM_UUID)
                                  .getAsString());
        }

        //TODO update locationDbId
    }
}
