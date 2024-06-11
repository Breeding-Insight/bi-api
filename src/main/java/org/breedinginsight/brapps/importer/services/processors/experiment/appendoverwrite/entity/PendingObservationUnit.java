package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity;

import io.micronaut.context.annotation.Prototype;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.ObservationUnitService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Prototype
public class PendingObservationUnit implements ExperimentImportEntity<BrAPIObservationUnit> {
    ExpUnitContext cache;
    ImportContext importContext;
    BrAPIObservationUnitDAO observationUnitDAO;
    ObservationUnitService observationUnitService;
    ExperimentUtilities experimentUtilities;
    @Inject
    public PendingObservationUnit(ExpUnitMiddlewareContext context,
                                  BrAPIObservationUnitDAO observationUnitDAO,
                                  ObservationUnitService observationUnitService,
                                  ExperimentUtilities experimentUtilities) {
        this.cache = context.getExpUnitContext();
        this.importContext = context.getImportContext();
        this.observationUnitDAO = observationUnitDAO;
        this.observationUnitService = observationUnitService;
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
    public List<BrAPIObservationUnit> brapiPost(List<BrAPIObservationUnit> members) throws ApiException, MissingRequiredInfoException, UnprocessableEntityException, DoesNotExistException {
        // TODO: move the germplasm foreign key assignment out one level
        // Set the germplasm foreign key for observation unit requests
        cache.getExistingGermplasmByGID().values()
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(PendingImportObject::getBrAPIObject)
                .forEach(germplasm -> {

                    // Collect all observation units that are associated with a germplasm GID
                    members.stream().filter(obsUnit -> germplasm.getAccessionNumber() != null &&
                                    germplasm.getAccessionNumber().equals(obsUnit
                                            .getAdditionalInfo().getAsJsonObject()
                                            .get(BrAPIAdditionalInfoFields.GID).getAsString()))

                            // Set the foreign key for each unit
                            .forEach(obsUnit -> obsUnit.setGermplasmDbId(germplasm.getGermplasmDbId()));
                });

        return observationUnitDAO.createBrAPIObservationUnits(members, importContext.getProgram().getId(), importContext.getUpload());
    }

    /**
     * Fetch objects required by the workflow from the BrAPI service.
     *
     * @return List of fetched entities
     * @throws ApiException if there is an issue with the API call
     */
    @Override
    public List<BrAPIObservationUnit> brapiRead() throws ApiException {
        // Collect deltabreed-generated exp unit ids listed in the import
        Set<String> expUnitIds = cache.getReferenceOUIds();

        // For each id fetch the observation unit from the brapi data store
        return observationUnitService.getObservationUnitsByDbId(new HashSet<>(expUnitIds), importContext.getProgram());
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
        return null;
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
    public List<BrAPIObservationUnit> getBrAPIState(ImportObjectState status) throws ApiException {
        return null;
    }

    /**
     * For workflow pending import objects of a given state, construct deep copies of the objects from the workflow context.
     *
     * @param status State of the objects
     * @return List of deep copies of entities from workflow context
     */
    @Override
    public List<BrAPIObservationUnit> copyWorkflowMembers(ImportObjectState status) {
        return experimentUtilities.copyWorkflowCachePendingBrAPIObjects(cache.getObservationUnitByNameNoScope(), BrAPIObservationUnit.class, status);
    }

    /**
     * For objects in the workflow context, update any foreign-key fields with values generated by the BrAPI service.
     *
     * @param members List of entities to be updated
     */
    @Override
    public <U> void updateWorkflow(List<U> members) {
        // Check if the input list is of type List<BrAPIObservationUnit>
        if (experimentUtilities.isInvalidMemberListForClass(members, BrAPIObservationUnit.class)) {
            return;
        }

        for (U member : members) {
            BrAPIObservationUnit unit = (BrAPIObservationUnit) member;

            // Set the dbId for observation units
            String studyNameNoScope = Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getStudyName(), importContext.getProgram().getKey());
            String unitNameNoScope = Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getObservationUnitName(), importContext.getProgram().getKey());
            String key = studyNameNoScope + unitNameNoScope;
            cache.getObservationUnitByNameNoScope().get(key).getBrAPIObject().setObservationUnitDbId(unit.getObservationUnitDbId());

            // Set the unit dbId for observations connected with the unit, matching on environment and exp unit
            cache.getPendingObservationByHash().values()
                    .stream()
                    .filter(obs -> obs.getBrAPIObject()
                            .getAdditionalInfo() != null
                            && obs.getBrAPIObject()
                            .getAdditionalInfo()
                            .get(BrAPIAdditionalInfoFields.STUDY_NAME) != null
                            && obs.getBrAPIObject()
                            .getAdditionalInfo()
                            .get(BrAPIAdditionalInfoFields.STUDY_NAME)
                            .getAsString()
                            .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getStudyName(), importContext.getProgram().getKey()))
                            && Utilities.removeProgramKeyAndUnknownAdditionalData(obs.getBrAPIObject().getObservationUnitName(), importContext.getProgram().getKey())
                            .equals(Utilities.removeProgramKeyAndUnknownAdditionalData(unit.getObservationUnitName(), importContext.getProgram().getKey()))
                    )
                    .forEach(obs -> {
                        if (StringUtils.isBlank(obs.getBrAPIObject().getObservationUnitDbId())) {
                            obs.getBrAPIObject().setObservationUnitDbId(unit.getObservationUnitDbId());
                        }
                        obs.getBrAPIObject().setStudyDbId(unit.getStudyDbId());
                        obs.getBrAPIObject().setGermplasmDbId(unit.getGermplasmDbId());
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
        // Check if the input list is of type List<BrAPIObservationUnit>
        if (experimentUtilities.isInvalidMemberListForClass(members, BrAPIObservationUnit.class)) {
            return;
        }

        // Construct pending import objects from the units
        List<PendingImportObject<BrAPIObservationUnit>> pendingUnits = members.stream().map(u -> (BrAPIObservationUnit) u).map(observationUnitService::constructPIOFromBrapiUnit).collect(Collectors.toList());

        // Construct a hashmap to look up the pending unit by ID
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById = observationUnitService.mapPendingUnitById(new ArrayList<>(pendingUnits));

        // Construct a hashmap to look up the pending unit by Study+Unit names with program keys removed
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByNameNoScope = observationUnitService.mapPendingUnitByNameNoScope(new ArrayList<>(pendingUnits), importContext.getProgram());

        // add maps to the context for use in processing import
        cache.setPendingObsUnitByOUId(pendingUnitById);
        cache.setObservationUnitByNameNoScope(pendingUnitByNameNoScope);
    }
}
