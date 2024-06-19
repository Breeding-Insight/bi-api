package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware;

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
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
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
    public ExpUnitMiddlewareContext process(ExpUnitMiddlewareContext context) {

        return processNext(context);
    }

    @Override
    public ExpUnitMiddlewareContext compensate(ExpUnitMiddlewareContext context) {
        // tag an error if it occurred in this local transaction
        context.getExpUnitContext().getProcessError().tag(this.getClass().getName());

        // handle the error in the prior local transaction
        return compensatePrior(context);
    }
    private Map<String, PendingImportObject<BrAPIObservationUnit>> fetchReferenceObservationUnits(
            ExpUnitMiddlewareContext context) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById = new HashMap<>();
        try {
            // Retrieve reference Observation Units based on IDs
            List<BrAPIObservationUnit> referenceObsUnits = brAPIObservationUnitDAO.getObservationUnitsById(
                    new ArrayList<String>(context.getExpUnitContext().getReferenceOUIds()),
                    context.getImportContext().getProgram()
            );

            // Construct the DeltaBreed observation unit source for external references
            String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());

            if (referenceObsUnits.size() == context.getExpUnitContext().getReferenceOUIds().size()) {

                // Iterate through reference Observation Units
                for (BrAPIObservationUnit unit : referenceObsUnits) {// Get external reference for the Observation Unit
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
                                this.compensate(context);
                            }
                    );


                }
            } else {
                // Handle case of missing Observation Units in data store
                List<String> missingIds = new ArrayList<>(context.getExpUnitContext().getReferenceOUIds());
                Set<String> fetchedIds = referenceObsUnits.stream().map(unit ->
                                Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource)
                                        .orElseThrow(() -> new InternalServerException("External reference does not exist for Deltabreed ObservationUnit ID"))
                                        .getReferenceId())
                        .collect(Collectors.toSet());
                missingIds.removeAll(fetchedIds);

                // throw error reporting any reference IDs with no corresponding stored unit in the brapi data store
                this.compensate(context);
            }

            return pendingUnitById;
        } catch (ApiException e) {

            // throw an error if problem getting data from the brapi data store
            this.compensate(context);
        }
        return pendingUnitById;
    }
}
