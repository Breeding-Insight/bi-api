package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;

import java.util.Optional;

/**
 * Interface representing an action to be performed on the BrAPI service.
 * This interface defines two methods: execute() to execute the action on the BrAPI service
 * and return any relevant BrAPI state, and getEntity() to get the BrAPI entity being
 * acted on based on the provided ExpUnitMiddlewareContext.
 *
 * @param <T> The type of entity on which the action is being performed.
 */
public interface BrAPIAction<T> {

    /**
     * Execute the action on the BrAPI service.
     *
     * @return An Optional containing the relevant BrAPI state after executing the action.
     * @throws ApiException if an error occurs during the execution of the action.
     */
    Optional<BrAPIState> execute() throws ApiException;

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @param context The ExpUnitMiddlewareContext providing information about the entity.
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    ExperimentImportEntity<T> getEntity(ExpUnitMiddlewareContext context);
}


/*
 * Overall module description:
 * This BrAPIAction interface defines methods to handle actions on the BrAPI service.
 * Developers can implement this interface to execute actions and retrieve the entity being acted upon.
 * The execute() method is used to perform actions on the BrAPI service and return the resulting BrAPI state,
 * while the getEntity() method retrieves the BrAPI entity based on the provided ExpUnitMiddlewareContext.
 *
 * The BrAPIAction interface allows for customization of BrAPI service interactions and handling of BrAPI entities.
 */

