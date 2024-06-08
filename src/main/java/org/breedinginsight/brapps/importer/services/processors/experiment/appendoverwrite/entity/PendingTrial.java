package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.core.BrAPITrial;
import org.breedinginsight.brapi.v2.dao.BrAPITrialDAO;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.TrialService;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class PendingTrial implements ExperimentImportEntity<BrAPITrial> {
    ExpUnitContext cache;
    ImportContext importContext;
    @Inject
    TrialService trialService;
    @Inject
    BrAPITrialDAO brapiTrialDAO;
    @Inject
    ExperimentUtilities experimentUtilities;

    public PendingTrial(ExpUnitMiddlewareContext context) {
        this.cache = context.getExpUnitContext();
        this.importContext = context.getImportContext();
    }

    /**
     * Create new objects generated by the workflow in the BrAPI service.
     *
     * @param members List of entities to be created
     * @return List of created entities
     * @throws ApiException if there is an issue with the API call
     */
    @Override
    public List<BrAPITrial> brapiPost(List<BrAPITrial> members) throws ApiException {
        return brapiTrialDAO.createBrAPITrials(members, importContext.getProgram().getId(), importContext.getUpload());
    }

    /**
     * Fetch objects required by the workflow from the BrAPI service.
     *
     * @return List of fetched entities
     * @throws ApiException if there is an issue with the API call
     */
    @Override
    public List<BrAPITrial> brapiRead() throws ApiException {
        // Get the dbIds of the trials belonging to the required exp units
        Set<String> trialDbIds = cache.getObservationUnitByNameNoScope().values().stream()
                .map(pendingUnit -> trialService.getTrialDbIdBelongingToPendingUnit(pendingUnit, importContext.getProgram())).collect(Collectors.toSet());

        // Get the BrAPI trials belonging to required exp units
        return trialService.fetchBrapiTrialsByDbId(trialDbIds, importContext.getProgram());
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
        // Check if the input list is of type List<BrAPITrial>
        if (experimentUtilities.isInvalidMemberListForClass(members, BrAPITrial.class)) {
            return new ArrayList<U>();
        }

        List<BrAPITrial> updatedTrials = new ArrayList<>();
        for (U member : members) {
            BrAPITrial trial = (BrAPITrial) member;
            Optional.ofNullable(brapiTrialDAO.updateBrAPITrial(trial.getTrialDbId(), trial, importContext.getProgram().getId())).ifPresent(updatedTrials::add);
        }

        return (List<U>) updatedTrials;
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
        // TODO: implement delete for trials on BrapiJavaTestServer
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
    public List<BrAPITrial> getBrAPIState(ImportObjectState status) throws ApiException {
        List<String> ids = copyWorkflowMembers(status).stream().map(BrAPITrial::getTrialDbId).collect(Collectors.toList());
        return brapiTrialDAO.getTrialsByDbIds(ids, importContext.getProgram());
    }

    /**
     * For workflow pending import objects of a given state, construct deep copies of the objects from the workflow context.
     *
     * @param status State of the objects
     * @return List of deep copies of entities from workflow context
     */
    @Override
    public List<BrAPITrial> copyWorkflowMembers(ImportObjectState status) {
        return experimentUtilities.copyWorkflowCachePendingBrAPIObjects(cache.getTrialByNameNoScope(), BrAPITrial.class, status);
    }

    /**
     * For objects in the workflow context, update any foreign-key fields with values generated by the BrAPI service.
     *
     * @param members List of entities to be updated
     */
    @Override
    public <U> void updateWorkflow(List<U> members) {
        // Check if the input list is of type List<BrAPITrial>
        if (experimentUtilities.isInvalidMemberListForClass(members, BrAPITrial.class)) {
            return;
        }

        // Update the workflow ref by setting the system-generated dbId for each newly created trial
        for (U member : members) {
            BrAPITrial trial = (BrAPITrial) member;
            String createdTrialNameNoScope = Utilities.removeProgramKey(trial.getTrialName(), importContext.getProgram().getKey());
            cache.getTrialByNameNoScope().get(createdTrialNameNoScope).getBrAPIObject().setTrialDbId(trial.getTrialDbId());
        }

        // Update trial DbIds in studies for all distinct trials
        cache.getTrialByNameNoScope().values().stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(trial ->
                        cache.getStudyByNameNoScope().values().stream()
                                .filter(study -> study.getBrAPIObject().getTrialName()
                                        .equals(Utilities.removeProgramKey(trial.getTrialName(), importContext.getProgram().getKey())))
                                .forEach(study -> study.getBrAPIObject().setTrialDbId(trial.getTrialDbId()))
                );
    }

    /**
     * Populate the workflow context with objects needed by the workflow.
     *
     * @param members List of entities to be initialized
     */
    @Override
    public <U> void initializeWorkflow(List<U> members) {
        // Check if the input list is of type List<BrAPITrial>
        if (experimentUtilities.isInvalidMemberListForClass(members, BrAPITrial.class)) {
            return;
        }

        // Construct the pending trials from the BrAPI trials
        List<PendingImportObject<BrAPITrial>> pendingTrials = members.stream()
                .map(t -> (BrAPITrial) t).map(trialService::constructPIOFromBrapiTrial).collect(Collectors.toList());

        // Construct a hashmap to look up the pending trial by trial name with the program key removed
        Map<String, PendingImportObject<BrAPITrial>> pendingTrialByNameNoScope = pendingTrials.stream()
                .collect(Collectors.toMap(pio -> Utilities.removeProgramKey(pio.getBrAPIObject().getTrialName(), importContext.getProgram().getKey()), pio -> pio));

        // Add the map to the context for use in processing import
        cache.setTrialByNameNoScope(pendingTrialByNameNoScope);
    }
}
