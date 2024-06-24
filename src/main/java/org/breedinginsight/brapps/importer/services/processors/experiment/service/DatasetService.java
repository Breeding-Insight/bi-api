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

package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListSummary;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.BrAPITrial;
import org.brapi.v2.model.core.response.BrAPIListDetails;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIListDAO;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.Trait;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class DatasetService {
    private final BrAPIListDAO brAPIListDAO;
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public DatasetService(BrAPIListDAO brapiListDAO) {
        this.brAPIListDAO = brapiListDAO;
    }
    /**
     * Module: Dataset Utility
     *
     * This module provides utility functions for interacting with datasets using the BrAPI standards.
     * It includes methods for fetching dataset details, creating new datasets, updating existing datasets, etc.
     * Usage: This module can be used in various applications where handling BrAPI-compliant datasets is required.
     */

    /**
     * Fetches dataset details by dataset ID and program
     *
     * This function fetches details of a dataset by its ID and associated program from a data source using the BrAPI standards.
     *
     * @param id The unique identifier of the dataset to fetch
     * @param program The program object associated with the dataset
     * @return BrAPIListDetails object containing the details of the dataset
     * @throws ApiException if there is an issue with fetching the dataset details from the data source
     */
    public Optional<BrAPIListDetails> fetchDatasetById(String id, Program program) throws ApiException {
        Optional<BrAPIListDetails> dataSetDetails = Optional.empty();

        // Retrieve existing dataset summaries based on program ID and external reference
        List<BrAPIListSummary> existingDatasets = brAPIListDAO
                .getListByTypeAndExternalRef(BrAPIListTypes.OBSERVATIONVARIABLES,
                        program.getId(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()),
                        UUID.fromString(id));

        // Check if the existing dataset summaries are returned, throw exception if not
        if (existingDatasets == null || existingDatasets.isEmpty()) {
            throw new InternalServerException("Existing dataset summary not returned from BrAPI server");
        }

        // Retrieve dataset details using the list DB ID from the existing dataset summary
        dataSetDetails = Optional.ofNullable(brAPIListDAO
                .getListById(existingDatasets.get(0).getListDbId(), program.getId())
                .getResult());

        return dataSetDetails;
    }

    /**
     * Constructs a PendingImportObject for a BrAPIListDetails dataset.
     * This method retrieves the external reference for the dataset from the existing list
     * based on a specific reference source. It then creates a PendingImportObject for the dataset
     * with the existing list and reference ID.
     *
     * @param dataset The BrAPIListDetails dataset for which to construct the PendingImportObject
     * @param program
     * @return A PendingImportObject containing the dataset with the existing list and reference ID
     * @throws IllegalStateException if external references weren't found for the list
     */
    public PendingImportObject<BrAPIListDetails> constructPIOFromDataset(BrAPIListDetails dataset, Program program) {
        // Get the external reference for the dataset from the existing list
        BrAPIExternalReference xref = Utilities.getExternalReference(dataset.getExternalReferences(),
                        String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.DATASET.getName()))
                .orElseThrow(() -> new IllegalStateException("External references weren't found for list (dbid): " + dataset.getListDbId()));

        // Create a PendingImportObject for the dataset with the existing list and reference ID
        return new PendingImportObject<BrAPIListDetails>(ImportObjectState.EXISTING, dataset, UUID.fromString(xref.getReferenceId()));
    }
}
