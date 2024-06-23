package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.entity;

import io.micronaut.context.annotation.Prototype;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.AppendOverwriteWorkflowContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;

import java.util.*;
import java.util.stream.Collectors;

@Prototype
public class PendingStudy implements ExperimentImportEntity<BrAPIStudy>{
    AppendOverwriteWorkflowContext cache;
    ImportContext importContext;
    StudyService studyService;
    BrAPIStudyDAO brAPIStudyDAO;
    ExperimentUtilities experimentUtilities;

    public PendingStudy(AppendOverwriteMiddlewareContext context,
                        StudyService studyService,
                        BrAPIStudyDAO brAPIStudyDAO,
                        ExperimentUtilities experimentUtilities) {
        this.cache = context.getAppendOverwriteWorkflowContext();
        this.importContext = context.getImportContext();
        this.studyService = studyService;
        this.brAPIStudyDAO = brAPIStudyDAO;
        this.experimentUtilities = experimentUtilities;
    }
    /**
     * Create new objects generated by the workflow in the BrAPI service.
     *
     * @param members List of entities to be created
     * @return List of created entities
     * @throws ApiException if there is an issue with the API call
     */
    @Override
    public List<BrAPIStudy> brapiPost(List<BrAPIStudy> members) throws ApiException, MissingRequiredInfoException, UnprocessableEntityException, DoesNotExistException {
        return brAPIStudyDAO.createBrAPIStudies(members, importContext.getProgram().getId(), importContext.getUpload());
    }

    /**
     * Fetch objects required by the workflow from the BrAPI service.
     *
     * @return List of fetched entities
     * @throws ApiException if there is an issue with the API call
     */
    @Override
    public List<BrAPIStudy> brapiRead() throws ApiException {
        // Get the dbIds of the studies belonging to the required exp units
        Set<String> studyDbIds = cache.getObservationUnitByNameNoScope().values().stream().map(studyService::getStudyDbIdBelongingToPendingUnit).collect(Collectors.toSet());
        return studyService.fetchBrapiStudiesByDbId(studyDbIds, importContext.getProgram());
    }

    /**
     * Commit objects changed by the workflow to the BrAPI service.
     *
     * @param members List of entities to be updated
     * @return List of updated entities
     * @throws ApiException             if there is an issue with the API call
     * @throws IllegalArgumentException if method arguments are invalid
     */
    @Override
    public <U> List<U> brapiPut(List<U> members) throws ApiException, IllegalArgumentException {
        return new ArrayList<>();
    }

    /**
     * Remove objects created by the workflow from the BrAPI service.
     *
     * @param members List of entities to be deleted
     * @return true if deletion is successful, false otherwise
     * @throws ApiException if there is an issue with the API call
     */
    @Override
    public <U> boolean brapiDelete(List<U> members) throws ApiException {
        // TODO: implement delete study endpoint on BrAPIJavaTestServer
        return false;
    }

    /**
     * For workflow pending import objects of a given state, fetch deep copies of the objects from the BrAPI service.
     *
     * @param status State of the objects
     * @return List of deep copies of entities
     * @throws ApiException if there is an issue with the API call
     */
    @Override
    public List<BrAPIStudy> getBrAPIState(ImportObjectState status) throws ApiException {
        return new ArrayList<>();
    }

    /**
     * For workflow pending import objects of a given state, construct deep copies of the objects from the workflow context.
     *
     * @param status State of the objects
     * @return List of deep copies of entities from workflow context
     */
    @Override
    public List<BrAPIStudy> copyWorkflowMembers(ImportObjectState status) {
        return experimentUtilities.copyWorkflowCachePendingBrAPIObjects(cache.getStudyByNameNoScope(), BrAPIStudy.class, status);
    }

    /**
     * For objects in the workflow context, update any foreign-key fields with values generated by the BrAPI service.
     *
     * @param members List of entities to be updated
     */
    @Override
    public <U> void updateWorkflow(List<U> members) {
        // Check if the input list is of type List<BrAPIStudy>
        if (experimentUtilities.isInvalidMemberListForClass(members, BrAPIStudy.class)) {
            return;
        }

        for (U member : members) {
            BrAPIStudy study = (BrAPIStudy) member;

            // set the DbId for each newly created study
            String createdStudy_name_no_key = Utilities.removeProgramKeyAndUnknownAdditionalData(study.getStudyName(), importContext.getProgram().getKey());
            cache.getStudyByNameNoScope().get(createdStudy_name_no_key).getBrAPIObject().setStudyDbId(study.getStudyDbId());

            // Set the study dbId for observation units
            cache.getObservationUnitByNameNoScope().values()
                    .stream()
                    .filter(obsUnit -> obsUnit.getBrAPIObject()
                            .getStudyName()
                            .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(study.getStudyName(), importContext.getProgram().getKey())))
                    .forEach(obsUnit -> {
                        obsUnit.getBrAPIObject().setStudyDbId(study.getStudyDbId());
                        obsUnit.getBrAPIObject().setTrialDbId(study.getTrialDbId());
                    });
        }
    }

    /**
     * Populate the workflow context with objects needed by the workflow.
     *
     * @param members List of entities to be initialized
     */
    @Override
    public <U> void initializeWorkflow(List<U> members) {
        // Check if the input list is of type List<BrAPIStudy>
        if (experimentUtilities.isInvalidMemberListForClass(members, BrAPIStudy.class)) {
            return;
        }

        // Construct the pending studies from the BrAPI studies
        List<PendingImportObject<BrAPIStudy>> pendingStudies = members.stream()
                .map(s->(BrAPIStudy) s)
                .map(pio -> studyService.constructPIOFromBrapiStudy(pio, importContext.getProgram())).collect(Collectors.toList());

        // Construct a hashmap to look up the pending study by study name with the program key removed
        Map<String, PendingImportObject<BrAPIStudy>> pendingStudyByNameNoScope = pendingStudies.stream()
                .collect(Collectors.toMap(pio -> Utilities.removeProgramKeyAndUnknownAdditionalData(pio.getBrAPIObject().getStudyName(), importContext.getProgram().getKey()), pio -> pio));

        // Construct a hashmap to look up the pending study by the observation unit ID of a unit stored in the BrAPI service
        Map<String, PendingImportObject<BrAPIStudy>> pendingStudyByOUId = cache.getPendingObsUnitByOUId().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> Optional.ofNullable(e.getValue().getBrAPIObject().getStudyName())
                                .map(studyNameScoped -> Utilities.removeProgramKeyAndUnknownAdditionalData(studyNameScoped, importContext.getProgram().getKey()))
                                .map(nameNoScope -> pendingStudyByNameNoScope.get(nameNoScope))
                                .orElseThrow(() -> new IllegalStateException("Observation unit missing study name: " + e.getKey()))));

        // Add the maps to the context for use in processing import
        cache.setStudyByNameNoScope(pendingStudyByNameNoScope);
        cache.setPendingStudyByOUId(pendingStudyByOUId);
    }
}
