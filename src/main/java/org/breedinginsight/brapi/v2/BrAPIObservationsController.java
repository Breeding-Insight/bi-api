package org.breedinginsight.brapi.v2;

import com.google.gson.Gson;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.ApiResponse;
import org.brapi.client.v2.JSON;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIStudy;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponse;
import org.brapi.v2.model.pheno.response.BrAPIObservationListResponseResult;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.auth.ProgramSecured;
import org.breedinginsight.api.auth.ProgramSecuredRoleGroup;
import org.breedinginsight.api.auth.SecurityService;
import org.breedinginsight.brapi.v1.controller.BrapiVersion;
import org.breedinginsight.brapps.importer.daos.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.daos.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.daos.ImportDAO;
import org.breedinginsight.brapps.importer.daos.ImportMappingDAO;
import org.breedinginsight.brapps.importer.model.ImportProgress;
import org.breedinginsight.brapps.importer.model.ImportUpload;
import org.breedinginsight.brapps.importer.model.mapping.ImportMapping;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.ProgramService;
import org.breedinginsight.services.TraitService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.utilities.Utilities;
import org.jooq.DSLContext;
import org.jooq.JSONB;

import javax.inject.Inject;
import java.util.*;

@Slf4j
@Controller
@Secured(SecurityRule.IS_AUTHENTICATED)
public class BrAPIObservationsController {

    private static final String MAPPING_TYPE = "BrAPIObservationImport";
    private final String referenceSource;
    private final ImportDAO importDAO;
    private final DSLContext dsl;
    private final ImportMappingDAO importMappingDAO;
    private final SecurityService securityService;
    private final BrAPIStudyDAO studyDAO;
    private final BrAPIObservationUnitDAO ouDAO;
    private final TraitService traitService;
    private final ProgramService programService;

    private final Gson gson;

    @Inject
    public BrAPIObservationsController(@Property(name = "brapi.server.reference-source") String referenceSource,
                                       ImportDAO importDAO,
                                       DSLContext dsl,
                                       ImportMappingDAO importMappingDAO,
                                       SecurityService securityService,
                                       BrAPIStudyDAO studyDAO,
                                       BrAPIObservationUnitDAO ouDAO,
                                       TraitService traitService,
                                       ProgramService programService) {
        this.referenceSource = referenceSource;
        this.importDAO = importDAO;
        this.dsl = dsl;
        this.importMappingDAO = importMappingDAO;
        this.securityService = securityService;
        this.studyDAO = studyDAO;
        this.ouDAO = ouDAO;
        this.traitService = traitService;
        this.programService = programService;
        this.gson = new JSON().getGson();
    }

    @Post("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/observations")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<?> postObservations(@PathVariable("programId") UUID programId, List<BrAPIObservation> observations) {
        Optional<Program> program = programService.getById(programId);
        if (program.isEmpty()) {
            log.error(String.format("programId: %s not found", programId));
            return HttpResponse.notFound();
        }

        AuthenticatedUser actingUser = securityService.getUser();

        List<String> observationUnitDbIds = new ArrayList<>();
        List<String> studyDbIds = new ArrayList<>();
        Map<String, List<BrAPIObservation>> observationsByObsUnitId = new HashMap<>();

        observations.forEach(observation -> {
            observationUnitDbIds.add(observation.getObservationUnitDbId());
            studyDbIds.add(observation.getStudyDbId());
            if (!observationsByObsUnitId.containsKey(observation.getObservationUnitDbId())) {
                observationsByObsUnitId.put(observation.getObservationUnitDbId(), new ArrayList<>());
            }
            List<BrAPIObservation> obs = observationsByObsUnitId.get(observation.getObservationUnitDbId());
            obs.add(observation);
        });

        Map<String, BrAPIObservationUnit> obsUnitByDbId = fetchObservationUnits(observationUnitDbIds, program.get());
        Map<String, BrAPIStudy> studiesByDbId = fetchStudies(studyDbIds, program.get());
        Map<String, Trait> traitsByDbId = fetchTraits(program.get());

        for (String observationUnitDbId : observationsByObsUnitId.keySet()) {
            BrAPIObservationUnit unit = obsUnitByDbId.get(observationUnitDbId);
            if (unit == null) {
                log.error(String.format("ObservationUnitDbId %s does not exist", observationUnitDbId));
                return HttpResponse.notFound(String.format("ObservationUnitDbId %s does not exist", observationUnitDbId));
            }

            Optional<BrAPIExternalReference> obsUnitId = Utilities.getExternalReference(unit.getExternalReferences(), String.format("%s/%s", referenceSource, ExternalReferenceSource.OBSERVATION_UNITS));
            if (obsUnitId.isEmpty()) {
                log.error(String.format("ObservationUnitDbId %s does not have a BI external reference does not exist", observationUnitDbId));
                return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, String.format("ObservationUnitDbId %s in an invalid state", observationUnitDbId));
            }

            List<BrAPIObservation> ouObs = observationsByObsUnitId.get(observationUnitDbId);

            for (BrAPIObservation observation : ouObs) {
                if (!studiesByDbId.containsKey(observation.getStudyDbId())) {
                    log.warn(String.format("StudyDbId %s does not exist", observation.getStudyDbId()));
                    return HttpResponse.notFound(String.format("StudyDbId %s does not exist", observation.getStudyDbId()));
                }

                observation.setObservationUnitName(unit.getObservationUnitName());

                Trait trait = traitsByDbId.get(observation.getObservationVariableDbId());
                if (trait == null) {
                    log.warn(String.format("ObservationVariableDbId %s does not exist", observation.getObservationVariableDbId()));
                    return HttpResponse.notFound(String.format("ObservationVariableDbId %s does not exist", observation.getObservationVariableDbId()));
                }
                observation.setObservationVariableName(trait.getObservationVariableName());

                observation.setObservationDbId(UUID.randomUUID()
                                                   .toString());
                if (observation.getExternalReferences() == null) {
                    observation.setExternalReferences(new ArrayList<>());
                }
                observation.getExternalReferences()
                           .add(new BrAPIExternalReference().referenceSource(String.format("%s/%s", referenceSource, ExternalReferenceSource.OBSERVATIONS))
                                                            .referenceID(observation.getObservationDbId()));
            }
        }

        try {
            saveImport(observations, programId, actingUser);
        } catch (Exception e) {
            log.error("Unable to save BrAPI observations", e);
            throw new InternalServerException("Unable to save observations", e);
        }

        ApiResponse<BrAPIObservationListResponse> brapiResponse = new ApiResponse<>(HttpStatus.OK.getCode(), Map.of(), new BrAPIObservationListResponse().result(new BrAPIObservationListResponseResult().data(observations)));

        return HttpResponse.ok(brapiResponse);
    }

    private void saveImport(List<BrAPIObservation> observations, UUID programId, AuthenticatedUser actingUser) {
        ImportMapping mapping = importMappingDAO.getSystemMappingByName(MAPPING_TYPE)
                                                .get(0);
        // Create our import progress object
        dsl.transactionResult(configuration -> {
            ImportUpload newUpload = new ImportUpload();
            newUpload.setProgramId(programId);
            String jsonString = gson.toJson(observations);
            JSONB jsonb = JSONB.valueOf(jsonString);
            newUpload.setMappedData(jsonb);
            newUpload.setImporterMappingId(mapping.getId());
            newUpload.setUserId(actingUser.getId());
            newUpload.setCreatedBy(actingUser.getId());
            newUpload.setUpdatedBy(actingUser.getId());

            // Create a progress object
            ImportProgress importProgress = new ImportProgress();
            importProgress.setCreatedBy(actingUser.getId());
            importProgress.setUpdatedBy(actingUser.getId());
            importProgress.setStatuscode((short) HttpStatus.OK.getCode());
            importDAO.createProgress(importProgress);

            newUpload.setImporterProgressId(importProgress.getId());
            importDAO.insert(newUpload);
            return newUpload;
        });
    }

    private Map<String, BrAPIObservationUnit> fetchObservationUnits(List<String> observationUnitDbIds, Program program) {
        Map<String, BrAPIObservationUnit> ousById = new HashMap<>();
        try {
            List<BrAPIObservationUnit> ous = ouDAO.getObservationUnitsByDbId(observationUnitDbIds, program);
            ous.forEach(ou -> ousById.put(ou.getObservationUnitDbId(), ou));
        } catch (ApiException e) {
            log.error("Error fetching observation units", e);
            Utilities.generateApiExceptionLogMessage(e);
            throw new InternalServerException("Error fetching observation units", e);
        }
        return ousById;
    }

    private Map<String, BrAPIStudy> fetchStudies(List<String> studyDbIds, Program program) {
        Map<String, BrAPIStudy> studiesById = new HashMap<>();
        try {
            List<BrAPIStudy> studies = studyDAO.getStudyByDbId(studyDbIds, program);
            studies.forEach(brAPIStudy -> studiesById.put(brAPIStudy.getStudyDbId(), brAPIStudy));
        } catch (ApiException e) {
            log.error("Error fetching study", e);
            Utilities.generateApiExceptionLogMessage(e);
            throw new InternalServerException("Error fetching study", e);
        }
        return studiesById;
    }

    private Map<String, Trait> fetchTraits(Program program) {
        Map<String, Trait> traitsById = new HashMap<>();
        try {
            List<Trait> traits = traitService.getByProgramId(program.getId(), true);
            traits.forEach(trait -> traitsById.put(trait.getObservationVariableDbId(), trait));
        } catch (DoesNotExistException e) {
            log.error("Error fetching traits", e);
            throw new InternalServerException("Error fetching traits", e);
        }
        return traitsById;
    }

    @Put("/${micronaut.bi.api.version}/programs/{programId}" + BrapiVersion.BRAPI_V2 + "/observations{?queryParams*}")
    @Produces(MediaType.APPLICATION_JSON)
    @ProgramSecured(roleGroups = {ProgramSecuredRoleGroup.ALL})
    public HttpResponse<ApiResponse<BrAPIObservationListResponse>> putObservations(@PathVariable("programId") UUID programId) {
        //TODO create an import object, and store that in the import tables in bidb
        //need to think through how to update pending observations vs approved and stored observations
        //for pending, how do the observations get looked up programmatically to overwrite?
        throw new UnsupportedOperationException();
    }
}
