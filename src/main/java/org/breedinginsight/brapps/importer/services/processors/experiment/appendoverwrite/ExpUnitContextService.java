package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessErrorConstants;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ExpUnitContextService {
    private final BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public ExpUnitContextService(BrAPIObservationUnitDAO brAPIObservationUnitDAO) {
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
    }

    /**
     * Retrieves reference Observation Units based on a set of reference Observation Unit IDs and a Program.
     * Constructs DeltaBreed observation unit source for external references and sets up pending Observation Units.
     *
     * @param referenceOUIds A set of reference Observation Unit IDs to retrieve
     * @param program The Program associated with the Observation Units
     * @return A Map containing pending Observation Units by their ID
     * @throws ApiException if an error occurs during the process
     */
    public Map<String, PendingImportObject<BrAPIObservationUnit>> fetchReferenceObservationUnits(
            Set<String> referenceOUIds,
                              Program program
    ) throws ApiException {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById = new HashMap<>();
        try {
            // Retrieve reference Observation Units based on IDs
            List<BrAPIObservationUnit> referenceObsUnits = brAPIObservationUnitDAO.getObservationUnitsById(
                    new ArrayList<String>(referenceOUIds),
                    program
            );

            // Construct the DeltaBreed observation unit source for external references
            String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());

            if (referenceObsUnits.size() == referenceOUIds.size()) {
                for (BrAPIObservationUnit unit : referenceObsUnits) {// Iterate through reference Observation Units

                    // Get external reference for the Observation Unit
                    Optional<BrAPIExternalReference> unitXref = Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource);
                    unitXref.ifPresentOrElse(
                            xref -> {

                                // Set pending Observation Unit by its ID
                                pendingUnitById.put(
                                        xref.getReferenceId(),
                                        new PendingImportObject<>(
                                                ImportObjectState.EXISTING, unit, UUID.fromString(xref.getReferenceId()))
                                );
                            },
                            () -> {

                                // but throw an error if no unit ID
                                throw new IllegalStateException("External reference does not exist for Deltabreed ObservationUnit ID");
                            }
                    );
                }
            } else {// Handle case of missing Observation Units in data store
                List<String> missingIds = new ArrayList<>(referenceOUIds);
                Set<String> fetchedIds = referenceObsUnits.stream()
                        .filter(unit ->Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource).isPresent())
                        .map(unit->Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource).get().getReferenceId())
                        .collect(Collectors.toSet());
                missingIds.removeAll(fetchedIds);

                // throw error reporting any reference IDs with no corresponding stored unit in the brapi data store
                throw new IllegalStateException("Observation Units not found for ObsUnitId(s): " + String.join(ExpImportProcessErrorConstants.COMMA_DELIMITER, missingIds));
            }

            return pendingUnitById;
        } catch (ApiException e) {
            log.error("Error fetching observation units: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new ApiException(e);
        }
    }
}
