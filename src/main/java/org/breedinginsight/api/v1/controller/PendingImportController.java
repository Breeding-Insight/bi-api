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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.JSON;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.model.v1.response.DataResponse;
import org.breedinginsight.api.model.v1.response.Response;
import org.breedinginsight.api.model.v1.response.metadata.Metadata;
import org.breedinginsight.api.model.v1.response.metadata.Pagination;
import org.breedinginsight.api.model.v1.response.metadata.Status;
import org.breedinginsight.api.model.v1.response.metadata.StatusCode;
import org.breedinginsight.brapps.importer.daos.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.daos.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.imports.PendingImport;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.ImportResponse;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.utilities.Utilities;
import org.jooq.JSONB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class PendingImportController {
    private static final String MAPPING_TYPE = "BrAPIObservationImport";

    private final ImportDAO importDAO;
    private final BrAPITrialDAO trialDAO;
    private final BrAPIStudyDAO studyDAO;
    private final ProgramService programService;
    private final Gson gson;
    private final String referenceSource;

    public PendingImportController(@Property(name = "brapi.server.reference-source") String referenceSource, ImportDAO importDAO, BrAPITrialDAO trialDAO, BrAPIStudyDAO studyDAO, ProgramService programService) {
        this.referenceSource = referenceSource;
        this.importDAO = importDAO;
        this.trialDAO = trialDAO;
        this.studyDAO = studyDAO;
        this.programService = programService;
        this.gson = new JSON().getGson();
    }

    @Get("/programs/{programId}/pending-imports")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<ImportResponse>>> getPendingBrAPIImports(@PathVariable("programId") UUID programId) {
        try {
            Optional<Program> program = programService.getById(programId);
            if (program.isEmpty()) {
                return HttpResponse.notFound();
            }
            List<ImportResponse> pendingImports = importDAO.getUploadsByType(programId, MAPPING_TYPE)
                                                           .stream()
                                                           .filter(importUpload -> importUpload.getProgress()
                                                                                               .getStatuscode() == HttpStatus.ACCEPTED.getCode())
                                                           .map(importUpload -> createImportResponseObjects(importUpload, false, program.get()))
                                                           .collect(Collectors.toList());

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            Pagination pagination = new Pagination(pendingImports.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response<DataResponse<ImportResponse>> response = new Response<>(metadata, new DataResponse<>(pendingImports));
            return HttpResponse.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving pending imports", e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pending imports");
        }
    }

    @Get("/programs/{programId}/pending-imports/curate?importIds")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<Response<DataResponse<ImportResponse>>> getImportsToCurate(@PathVariable("programId") UUID programId, @QueryValue("importIds") List<String> importIds) {
        try {
            Optional<Program> program = programService.getById(programId);
            if (program.isEmpty()) {
                return HttpResponse.notFound();
            }
            List<ImportResponse> pendingImports = importDAO.getUploadsByType(programId, MAPPING_TYPE)
                                                           .stream()
                                                           .filter(importUpload -> importUpload.getProgress()
                                                                                               .getStatuscode() == HttpStatus.ACCEPTED.getCode() && importIds.contains(importUpload.getId().toString()))
                                                           .map(importUpload -> createImportResponseObjects(importUpload, true, program.get()))
                                                           .collect(Collectors.toList());

            List<Status> metadataStatus = new ArrayList<>();
            metadataStatus.add(new Status(StatusCode.INFO, "Successful Query"));
            Pagination pagination = new Pagination(pendingImports.size(), 1, 1, 0);
            Metadata metadata = new Metadata(pagination, metadataStatus);

            Response<DataResponse<ImportResponse>> response = new Response<>(metadata, new DataResponse<>(pendingImports));
            return HttpResponse.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving pending imports", e);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pending imports");
        }
    }

    private ImportResponse createImportResponseObjects(ImportUpload importUpload, boolean includeObservations, Program program) {
        JsonArray pendingObsJson = gson.fromJson(importUpload.getMappedData().data(), JsonArray.class);
        BrAPIObservation observation = gson.fromJson(pendingObsJson.get(0), BrAPIObservation.class);

        BrAPIStudy study = fetchStudy(observation.getStudyDbId(), program);
        BrAPITrial trial = fetchTrial(study, program);

        PendingImport pendingImport = new PendingImport();
        pendingImport.setTrial(new PendingImportObject<>(ImportObjectState.EXISTING, trial, null));
        pendingImport.setStudy(new PendingImportObject<>(ImportObjectState.EXISTING, study, null));

        if (includeObservations) {
            List<PendingImportObject<BrAPIObservation>> pendingObs = new ArrayList<>();
            pendingObsJson.forEach(jsonElement -> {
                BrAPIObservation obs = gson.fromJson(pendingObsJson.get(0), BrAPIObservation.class);
                pendingObs.add(new PendingImportObject<>(ImportObjectState.NEW, obs, null));
            });
            pendingImport.setObservations(pendingObs);
        }

        return ImportResponse.builder()
                             .importId(importUpload.getId())
                             .progress(importUpload.getProgress())
                             .preview(JSONB.valueOf(gson.toJson(pendingImport)))
                             .createdByUser(importUpload.getCreatedByUser())
                             .createdAt(importUpload.getCreatedAt())
                             .updatedByUser(importUpload.getUpdatedByUser())
                             .updatedAt(importUpload.getUpdatedAt())
                             .build();
    }

    private BrAPITrial fetchTrial(BrAPIStudy study, Program program) {
        Optional<BrAPIExternalReference> externalReference = Utilities.getExternalReference(study.getExternalReferences(), String.format("%s/%s", referenceSource, ExternalReferenceSource.TRIALS.getName()));
        if (externalReference.isPresent()) {
            try {
                Optional<BrAPITrial> trial = trialDAO.getTrialByExternalId(externalReference.get()
                                                                                            .getReferenceID(), program);
                if (trial.isPresent()) {
                    return trial.get();
                } else {
                    throw new InternalServerException(String.format("Trial could not be found for program: %s, experiment UUID: %s",
                                                                    program.getId(),
                                                                    externalReference.get()
                                                                                     .getReferenceID()));
                }
            } catch (ApiException e) {
                log.error("Error fetching study", e);
                Utilities.generateApiExceptionLogMessage(e);
                throw new InternalServerException(String.format("More than one trial was found for program: %s, externalId: %s",
                                                                program.getId(),
                                                                externalReference.get()
                                                                                 .getReferenceID()));
            }
        } else {
            log.error("Study is missing BI external reference");
            throw new InternalServerException("Study is missing BI external reference");
        }
    }

    private BrAPIStudy fetchStudy(String studyDbId, Program program) {
        try {
            List<BrAPIStudy> studies = studyDAO.getStudyByDbId(List.of(studyDbId), program);
            if (studies.size() == 1) {
                return studies.get(0);
            } else {
                throw new InternalServerException(String.format("More than one study was found for program: %s, studyDbId: %s",
                                                                program.getId(),
                                                                studyDbId));
            }
        } catch (ApiException e) {
            log.error("Error fetching study", e);
            Utilities.generateApiExceptionLogMessage(e);
            throw new InternalServerException("Error fetching study", e);
        }
    }
}
