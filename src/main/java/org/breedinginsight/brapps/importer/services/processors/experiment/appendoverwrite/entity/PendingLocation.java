package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.LocationService;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.services.ProgramLocationService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PendingLocation implements ExperimentImportEntity<ProgramLocation> {
    ExpUnitContext cache;
    ImportContext importContext;
    @Inject
    ProgramLocationService programLocationService;
    @Inject
    LocationService locationService;
    @Inject
    ExperimentUtilities experimentUtilities;
    public PendingLocation(ExpUnitMiddlewareContext context) {
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
    public List<ProgramLocation> brapiPost(List<ProgramLocation> members) throws ApiException, MissingRequiredInfoException, UnprocessableEntityException, DoesNotExistException {
        // Construct requests
        List<ProgramLocationRequest> locationRequests = members.stream()
                .map(location -> ProgramLocationRequest.builder()
                        .name(location.getName())
                        .build())
                .collect(Collectors.toList());

        // Create acting user
        AuthenticatedUser actingUser = new AuthenticatedUser(importContext.getUpload().getUpdatedByUser().getName(), new ArrayList<>(), importContext.getUpload().getUpdatedByUser().getId(), new ArrayList<>());

        return programLocationService.create(actingUser, importContext.getProgram().getId(), locationRequests);
    }

    /**
     * Fetch objects required by the workflow from the BrAPI service.
     *
     * @return List of fetched entities
     * @throws ApiException if there is an issue with the API call
     */
    @Override
    public List<ProgramLocation> brapiRead() throws ApiException {
        // Get the dbIds of the studies belonging to the required exp units
        Set<String> locationDbIds = cache.getStudyByNameNoScope().values().stream().map(pio -> pio.getBrAPIObject().getLocationDbId()).collect(Collectors.toSet());

        // Get the locations belonging to required exp units
        return locationService.fetchLocationsByDbId(locationDbIds, importContext.getProgram());
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
        // TODO: implement delete for program locations
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
    public List<ProgramLocation> getBrAPIState(ImportObjectState status) throws ApiException {
        return new ArrayList<>();
    }

    /**
     * For workflow pending import objects of a given state, construct deep copies of the objects from the workflow context.
     *
     * @param status State of the objects
     * @return List of deep copies of entities from workflow context
     */
    @Override
    public List<ProgramLocation> copyWorkflowMembers(ImportObjectState status) {
        return experimentUtilities.copyWorkflowCachePendingBrAPIObjects(cache.getLocationByName(), ProgramLocation.class, status);
    }

    /**
     * For objects in the workflow context, update any foreign-key fields with values generated by the BrAPI service.
     *
     * @param members List of entities to be updated
     */
    @Override
    public <U> void updateWorkflow(List<U> members) {
        // Check if the input list is of type List<ProgramLocation>
        if (experimentUtilities.isInvalidMemberListForClass(members, ProgramLocation.class)) {
            return;
        }

        for (U member : members) {
            ProgramLocation location = (ProgramLocation) member;

            // Set the system-generated dbId for each newly created location
            cache.getLocationByName().get(location.getName()).getBrAPIObject().setLocationDbId(location.getLocationDbId());

            // Set the location dbid for cached studies
            cache.getStudyByNameNoScope().values().stream()
                    .filter(study -> location.getId().toString().equals(study.getBrAPIObject().getLocationDbId()))
                    .forEach(study -> study.getBrAPIObject().setLocationDbId(location.getLocationDbId()));
        }

    }

    /**
     * Populate the workflow context with objects needed by the workflow.
     *
     * @param members List of entities to be initialized
     */
    @Override
    public <U> void initializeWorkflow(List<U> members) {
        // Check if the input list is of type List<ProgramLocation>
        if (experimentUtilities.isInvalidMemberListForClass(members, ProgramLocation.class)) {
            return;
        }

        // Construct the pending locations from the BrAPI locations
        List<PendingImportObject<ProgramLocation>> pendingLocations = members.stream().map((U brapiLocation) -> locationService.constructPIOFromBrapiLocation((ProgramLocation) brapiLocation)).collect(Collectors.toList());

        // Construct a hashmap to look up the pending location by location name
        Map<String, PendingImportObject<ProgramLocation>> pendingLocationByName = pendingLocations.stream().collect(Collectors.toMap(pio -> pio.getBrAPIObject().getName(), pio -> pio));

        // Add the map to the context for use in processing import
        cache.setLocationByName(pendingLocationByName);
    }
}
