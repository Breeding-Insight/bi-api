package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.GermplasmService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class PendingGermplasm implements ExperimentImportEntity<BrAPIGermplasm> {
    ExpUnitContext cache;
    ImportContext importContext;
    @Inject
    GermplasmService germplasmService;
    @Inject
    ExperimentUtilities experimentUtilities;

    public PendingGermplasm(ExpUnitMiddlewareContext context) {
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
    public List<BrAPIGermplasm> brapiPost(List<BrAPIGermplasm> members) throws ApiException, MissingRequiredInfoException, UnprocessableEntityException, DoesNotExistException {
        return null;
    }

    /**
     * Fetch objects required by the workflow from the BrAPI service.
     *
     * @return List of fetched entities
     * @throws ApiException if there is an issue with the API call
     */
    @Override
    public List<BrAPIGermplasm> brapiRead() throws ApiException {
        // Get the dbIds of the germplasm belonging to the required exp units
        Set<String> germplasmDbIds = cache.getObservationUnitByNameNoScope().values().stream().map(ou -> ou.getBrAPIObject().getGermplasmDbId()).collect(Collectors.toSet());

        // Get the dataset belonging to required exp units
        return germplasmService.fetchGermplasmByDbId(new HashSet<>(germplasmDbIds), importContext.getProgram());
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
    public List<BrAPIGermplasm> getBrAPIState(ImportObjectState status) throws ApiException {
        return new ArrayList<>();
    }

    /**
     * For workflow pending import objects of a given state, construct deep copies of the objects from the workflow context.
     *
     * @param status State of the objects
     * @return List of deep copies of entities from workflow context
     */
    @Override
    public List<BrAPIGermplasm> copyWorkflowMembers(ImportObjectState status) {
        return experimentUtilities.copyWorkflowCachePendingBrAPIObjects(cache.getExistingGermplasmByGID(), BrAPIGermplasm.class, status);
    }

    /**
     * For objects in the workflow context, update any foreign-key fields with values generated by the BrAPI service.
     *
     * @param members List of entities to be updated
     */
    @Override
    public <U> void updateWorkflow(List<U> members) {

    }

    /**
     * Populate the workflow context with objects needed by the workflow.
     *
     * @param members List of entities to be initialized
     */
    @Override
    public <U> void initializeWorkflow(List<U> members) {
        // Check if the input list is of type List<BrAPIGermplasm>
        if (experimentUtilities.isInvalidMemberListForClass(members, BrAPIGermplasm.class)) {
            return;
        }

        // Construct the pending germplasm from the BrAPI locations
        List<PendingImportObject<BrAPIGermplasm>> pendingGermplasm = members.stream().map(g -> (BrAPIGermplasm) g).map(germplasmService::constructPIOFromBrapiGermplasm).collect(Collectors.toList());

        // Construct a hashmap to look up the pending germplasm by gid
        Map<String, PendingImportObject<BrAPIGermplasm>> pendingGermplasmByGID = pendingGermplasm.stream().collect(Collectors.toMap(germplasmService::getGIDFromGermplasmPIO, pio -> pio));

        // Add the map to the context for use in processing import
        cache.setExistingGermplasmByGID(pendingGermplasmByGID);
    }
}
