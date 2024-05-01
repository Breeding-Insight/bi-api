package org.breedinginsight.brapps.importer.services.processors.experiment.middleware.ExpUnit;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.imports.BrAPIImport;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessErrorConstants;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class GetExistingBrAPIData extends ExpUnitMiddleware {
    private final BrAPIObservationUnitDAO brAPIObservationUnitDAO;
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    @Inject
    public GetExistingBrAPIData(BrAPIObservationUnitDAO brAPIObservationUnitDAO) {
        this.brAPIObservationUnitDAO = brAPIObservationUnitDAO;
    }

    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        return processNext(context);
    }

    private Map<String, PendingImportObject<BrAPIObservationUnit>> fetchReferenceObservationUnits(
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
                // Iterate through reference Observation Units
                referenceObsUnits.forEach(unit -> {
                    // Get external reference for the Observation Unit
                    BrAPIExternalReference unitXref = Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource)
                            .orElseThrow(() -> new IllegalStateException("External reference does not exist for Deltabreed ObservationUnit ID"));

                    // Set pending Observation Unit by its ID
                    pendingUnitById.put(
                            unitXref.getReferenceId(),
                            new PendingImportObject<BrAPIObservationUnit>(
                                    ImportObjectState.EXISTING, unit, UUID.fromString(unitXref.getReferenceId()))
                    );
                });
            } else {
                // Handle missing Observation Unit IDs
                List<String> missingIds = new ArrayList<>(referenceOUIds);
                Set<String> fetchedIds = referenceObsUnits.stream().map(unit ->
                                Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource)
                                        .orElseThrow(() -> new InternalServerException("External reference does not exist for Deltabreed ObservationUnit ID"))
                                        .getReferenceId())
                        .collect(Collectors.toSet());
                missingIds.removeAll(fetchedIds);
                throw new IllegalStateException("Observation Units not found for ObsUnitId(s): " + String.join(ExpImportProcessErrorConstants.COMMA_DELIMITER, missingIds));
            }

            return pendingUnitById;
        } catch (ApiException e) {
            log.error("Error fetching observation units: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new ApiException(e);
        }
    }
}
