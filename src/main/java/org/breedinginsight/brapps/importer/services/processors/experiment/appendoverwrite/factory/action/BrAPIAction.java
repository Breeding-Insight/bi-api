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

package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.action;

import org.brapi.client.v2.model.exceptions.ApiException;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.BrAPIState;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.factory.entity.ExperimentImportEntity;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

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
    Optional<BrAPIState> execute() throws ApiException, MissingRequiredInfoException, UnprocessableEntityException, DoesNotExistException;

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    ExperimentImportEntity<T> getEntity();
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

