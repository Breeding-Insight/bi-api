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
    public List<BrAPIObservationUnit> getReferenceUnits(Set<String> expUnitIds,
                                                        Program program) throws ApiException {
        // Retrieve reference Observation Units based on IDs
        return brAPIObservationUnitDAO.getObservationUnitsById(new ArrayList<String>(expUnitIds), program);
    }

    public PendingImportObject<BrAPIObservationUnit> constructPIOFromExistingUnit(BrAPIObservationUnit unit) {
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

    public Map<String, PendingImportObject<BrAPIObservationUnit>> mapPendingUnitById(List<PendingImportObject<BrAPIObservationUnit>> pios) {
        Map<String, PendingImportObject<BrAPIObservationUnit>> pendingUnitById = new HashMap<>();

        // Construct the DeltaBreed observation unit source for external references
        String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());

        for (PendingImportObject<BrAPIObservationUnit> pio : pios) {

            // Get external reference for the Observation Unit
            Optional<BrAPIExternalReference> xref = Utilities.getExternalReference(pio.getBrAPIObject().getExternalReferences(), deltaBreedOUSource);
            pendingUnitById.put(xref.get().getReferenceId(),pio);
        }

        return pendingUnitById;
    }

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
            pendingUnitByNameNoScope.put(ExperimentUtilities.createObservationUnitKey(studyName, observationUnitName), pio);
        }

        return pendingUnitByNameNoScope;
    }


    public List<String> collectMissingOUIds(Set<String> referenceIds, List<BrAPIObservationUnit> existingUnits) {
        List<String> missingIds = new ArrayList<>(referenceIds);

        // Construct the DeltaBreed observation unit source for external references
        String deltaBreedOUSource = String.format("%s/%s", BRAPI_REFERENCE_SOURCE, ExternalReferenceSource.OBSERVATION_UNITS.getName());

        Set<String> fetchedIds = existingUnits.stream()
                .filter(unit ->Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource).isPresent())
                .map(unit->Utilities.getExternalReference(unit.getExternalReferences(), deltaBreedOUSource).get().getReferenceId())
                .collect(Collectors.toSet());
        missingIds.removeAll(fetchedIds);

        return missingIds;
    }
}
