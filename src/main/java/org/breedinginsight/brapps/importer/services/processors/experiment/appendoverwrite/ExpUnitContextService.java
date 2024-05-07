package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite;

import io.micronaut.context.annotation.Property;
import lombok.extern.slf4j.Slf4j;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationUnitDAO;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.brapps.importer.services.ExternalReferenceSource;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.model.ExpUnitContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpImportProcessConstants;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ImportContext;
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


    public Map<String, PendingImportObject<BrAPIObservationUnit>> fetchReferenceObservationUnits(
            ImportContext importContext,
            ExpUnitContext expUnitContext
    ) throws ApiException {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById = new HashMap<>();
        try {
            // Retrieve reference Observation Units based on IDs
            List<BrAPIObservationUnit> referenceObsUnits = brAPIObservationUnitDAO.getObservationUnitsById(
                    new ArrayList<String>(expUnitContext.getReferenceOUIds()),
                    importContext.getProgram()
            );

            // Construct the DeltaBreed observation unit source for external references
            String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());

            if (referenceObsUnits.size() == expUnitContext.getReferenceOUIds().size()) {
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
                List<String> missingIds = new ArrayList<>(expUnitContext.getReferenceOUIds());
                Set<String> fetchedIds = referenceObsUnits.stream()
                        .filter(unit ->Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource).isPresent())
                        .map(unit->Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource).get().getReferenceId())
                        .collect(Collectors.toSet());
                missingIds.removeAll(fetchedIds);

                // throw error reporting any reference IDs with no corresponding stored unit in the brapi data store
                throw new IllegalStateException("Observation Units not found for ObsUnitId(s): " + String.join(ExpImportProcessConstants.COMMA_DELIMITER, missingIds));
            }

            return pendingUnitById;
        } catch (ApiException e) {
            log.error("Error fetching observation units: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new ApiException(e);
        }
    }

    private Map<String, PendingImportObject<BrAPIObservationUnit>> mapPendingObservationUnitByName(
            ExpUnitContext expUnitContext,
            ImportContext importContext
    ) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitByName = new HashMap<>();
        for (Map.Entry<String, PendingImportObject<BrAPIObservationUnit>> entry : expUnitContext.getPendingObsUnitByOUId().entrySet()) {
            String studyName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    entry.getValue().getBrAPIObject().getStudyName(),
                    importContext.getProgram().getKey()
            );
            String observationUnitName = Utilities.removeProgramKeyAndUnknownAdditionalData(
                    entry.getValue().getBrAPIObject().getObservationUnitName(),
                    importContext.getProgram().getKey()
            );
            pendingUnitByName.put(ExperimentUtilities.createObservationUnitKey(studyName, observationUnitName), entry.getValue());
        }
        return pendingUnitByName;
    }
}
