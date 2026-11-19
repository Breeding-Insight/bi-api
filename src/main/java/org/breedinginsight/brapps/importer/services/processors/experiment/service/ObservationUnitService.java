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
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.EntityNotFoundException;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class ObservationUnitService {
    private final BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public ObservationUnitService(BrAPIObservationUnitDAO brAPIObservationUnitDAO) {
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
    }

    /**
     * Retrieves a list of BrAPI (Breeding API) observation units by their database IDs for a given set of observation unit IDs and program.
     *
     * This method queries the BrAPIObservationUnitDAO to retrieve BrAPI observation units based on the provided observation unit IDs and program.
     * If the database IDs of the retrieved BrAPI observation units do not match the provided observation unit IDs, an IllegalStateException is thrown.
     * The exception includes information on the missing observation unit database IDs.
     *
     * @param obsUnitIds a set of observation unit IDs for which to retrieve BrAPI observation units
     * @param program the program for which to retrieve BrAPI observation units
     * @return a list of BrAPIObservationUnit objects corresponding to the provided observation unit IDs
     * @throws ApiException if an error occurs during the retrieval of observation units
     * @throws IllegalStateException if the retrieved observation units do not match the provided observation unit IDs
     */
    public List<BrAPIObservationUnit> getObservationUnitsById(Set<String> obsUnitIds, Program program) throws ApiException, IllegalStateException, EntityNotFoundException {
        List<BrAPIObservationUnit> brapiUnits = null;

        // Retrieve reference Observation Units based on IDs
        brapiUnits = brAPIObservationUnitDAO.getObservationUnitsById(obsUnitIds, program);

        // If no BrAPI units are found, throw an EntityNotFoundException with an error message
        if (obsUnitIds.size() != brapiUnits.size()) {
            Set<String> missingIds = new HashSet<>(obsUnitIds);

            // Calculate missing IDs based on retrieved BrAPI units
            missingIds.removeAll(brapiUnits.stream().map(BrAPIObservationUnit::getObservationUnitDbId).collect(Collectors.toSet()));

            // Throw exception with missing IDs information
            throw new EntityNotFoundException(missingIds);
        }

        return brapiUnits;
    }

    /**
     * Constructs a PendingImportObject of type BrAPIObservationUnit from a given BrAPIObservationUnit object.
     * This function is responsible for constructing an import object that represents an observation unit for the Deltabreed system,
     * using a provided BrAPIObservationUnit object from a BrAPI source.
     *
     * @param unit the BrAPIObservationUnit object to be used for constructing the PendingImportObject
     * @return a PendingImportObject of type BrAPIObservationUnit representing the imported observation unit
     * @throws IllegalStateException if the external reference for the observation unit does not exist
     */
    public PendingImportObject<BrAPIObservationUnit> constructPIOFromBrapiUnit(BrAPIObservationUnit unit) {
        final PendingImportObject<BrAPIObservationUnit>[] pio = new PendingImportObject[]{null};

        // Construct the DeltaBreed observation unit source for external references
        String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());

        // Get external reference for the Observation Unit
        Optional<BrAPIExternalReference> unitXref = Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource);
        unitXref.ifPresentOrElse(
                xref -> {
                    pio[0] = new PendingImportObject<BrAPIObservationUnit>(ImportObjectState.EXISTING, unit, UUID.fromString(xref.getReferenceId()));
                },
                () -> {

                    // but throw an error if no unit ID
                    throw new IllegalStateException("External reference does not exist for Deltabreed ObservationUnit ID");
                }
        );
        return pio[0];
    }

    /**
     * This method takes a list of PendingImportObject<BrAPIObservationUnit> objects and a Program object as input
     * and maps the PendingImportObject<BrAPIObservationUnit> objects by their observation unit name without the program scope.
     *
     * @param pios A list of PendingImportObject<BrAPIObservationUnit> objects to be processed
     * @param program The Program object representing the scope to be removed from observation unit names
     * @return A Map<String, PendingImportObject<BrAPIObservationUnit>> mapping observation unit names without the program scope to the corresponding PendingImportObject<BrAPIObservationUnit> objects
     */
    public Map<String, PendingImportObject<BrAPIObservationUnit>> mapPendingUnitByNameNoScope(List<PendingImportObject<BrAPIObservationUnit>> pios,
                                                                                              Program program) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByNameNoScope = new HashMap<>();

        for (PendingImportObject<BrAPIObservationUnit> pio : pios) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    pio.getBrAPIObject().getStudyName(),
                    program.getKey()
            );
            String observationUnitName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    pio.getBrAPIObject().getObservationUnitName(),
                    program.getKey()
            );
            String germplasmGID = pio.getBrAPIObject().getAdditionalInfo().get("gid").getAsString();

            pendingUnitByNameNoScope.put(ExperimentUtilities.createObservationUnitKey(studyName, observationUnitName, germplasmGID), pio);
        }

        return pendingUnitByNameNoScope;
    }
}
