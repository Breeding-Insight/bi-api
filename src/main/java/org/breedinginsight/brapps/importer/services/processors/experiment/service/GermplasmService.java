package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapi.v2.dao.BrAPIGermplasmDAO;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class GermplasmService {
    private final BrAPIGermplasmDAO germplasmDAO;
    @Property(name = "brapi.server.reference-source")
    private String BRAPI_REFERENCE_SOURCE;

    public GermplasmService(BrAPIGermplasmDAO germplasmDAO) {
        this.germplasmDAO = germplasmDAO;
    }

    /**
     * Retrieves a list of BrAPI Germplasm objects based on the provided set of database IDs and a Program object.
     *
     * @param dbIds A Set of database IDs (strings) used to filter germplasm data retrieval.
     * @param program The Program object representing the program associated with the germplasm data.
     * @return A List of BrAPIGermplasm objects that match the provided database IDs and program.
     * @throws ApiException If an error occurs during the retrieval process.
     */
    public List<BrAPIGermplasm> fetchGermplasmByDbId(Set<String> dbIds, Program program) throws ApiException {
        List<BrAPIGermplasm> brapiGermplasm = null;
        brapiGermplasm = germplasmDAO.getGermplasmsByDBID(dbIds, program.getId());
        return brapiGermplasm;
    }

    /**
     * This method constructs a PendingImportObject for a given BrAPI Germplasm.
     * It retrieves the External Reference associated with the Germplasm and constructs a PendingImportObject with ImportObjectState set to EXISTING.
     *
     * @param brapiGermplasm The BrAPI Germplasm object for which the PendingImportObject needs to be constructed
     * @return PendingImportObject<BrAPIGermplasm> A PendingImportObject containing the BrAPI Germplasm object and its External Reference
     * @throws IllegalStateException if the External Reference for the Germplasm is not found
     */
    public PendingImportObject<BrAPIGermplasm> constructPIOFromBrapiGermplasm(BrAPIGermplasm brapiGermplasm) {
        // Initialize the PendingImportObject to null
        PendingImportObject<BrAPIGermplasm> pio = null;

        // Retrieve the External Reference associated with the Germplasm from the Utilities class
        BrAPIExternalReference xref = Utilities.getExternalReference(brapiGermplasm.getExternalReferences(), String.format("%s", BRAPI_REFERENCE_SOURCE))
                // Throw an exception if External Reference is not found
                .orElseThrow(() -> new IllegalStateException("External references weren't found for germplasm (dbid): " + brapiGermplasm.getGermplasmDbId()));

        // Construct the PendingImportObject with ImportObjectState set to EXISTING and External Reference UUID
        pio = new PendingImportObject<>(ImportObjectState.EXISTING, brapiGermplasm, UUID.fromString(xref.getReferenceId()));

        return pio;
    }

    /**
     * Retrieves the Germplasm ID from a PendingImportObject containing BrAPI Germplasm data.
     * This method extracts the Germplasm ID (GID) from the Accession Number of the BrAPI Germplasm object within the PendingImportObject.
     *
     * @param pio a PendingImportObject that wraps BrAPI Germplasm data
     * @return a String representing the Germplasm ID extracted from the Accession Number
     */
    public String getGIDFromGermplasmPIO(PendingImportObject<BrAPIGermplasm> pio) {
        String gid = null;
        gid = pio.getBrAPIObject().getAccessionNumber();
        return gid;
    }

    // TODO: used by expunit workflow
//    public Map<String, PendingImportObject<BrAPIGermplasm>> initializeGermplasmByGIDForExistingObservationUnits(
//            Map<String, PendingImportObject<BrAPIObservationUnit>> unitByName,
//            Program program) {
//        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();
//
//        List<BrAPIGermplasm> existingGermplasms = new ArrayList<>();
//        if(unitByName.size() > 0) {
//            Set<String> germplasmDbIds = unitByName.values().stream().map(ou -> ou.getBrAPIObject().getGermplasmDbId()).collect(Collectors.toSet());
//            try {
//                existingGermplasms.addAll(brAPIGermplasmDAO.getGermplasmsByDBID(germplasmDbIds, program.getId()));
//            } catch (ApiException e) {
//                log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
//                throw new InternalServerException(e.toString(), e);
//            }
//        }
//
//        existingGermplasms.forEach(existingGermplasm -> {
//            BrAPIExternalReference xref = Utilities.getExternalReference(existingGermplasm.getExternalReferences(), String.format("%s", BRAPI_REFERENCE_SOURCE))
//                    .orElseThrow(() -> new IllegalStateException("External references wasn't found for germplasm (dbid): " + existingGermplasm.getGermplasmDbId()));
//            existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm, UUID.fromString(xref.getReferenceId())));
//        });
//        return existingGermplasmByGID;
//    }

    // TODO: used by create worflow
//    public Map<String, PendingImportObject<BrAPIGermplasm>> initializeExistingGermplasmByGID(Program program, List<ExperimentObservation> experimentImportRows) {
//        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();
//
//        List<BrAPIGermplasm> existingGermplasms = new ArrayList<>();
//        if(observationUnitByNameNoScope.size() > 0) {
//            Set<String> germplasmDbIds = observationUnitByNameNoScope.values().stream().map(ou -> ou.getBrAPIObject().getGermplasmDbId()).collect(Collectors.toSet());
//            try {
//                existingGermplasms.addAll(brAPIGermplasmDAO.getGermplasmsByDBID(germplasmDbIds, program.getId()));
//            } catch (ApiException e) {
//                log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
//                throw new InternalServerException(e.toString(), e);
//            }
//        }
//
//        List<String> uniqueGermplasmGIDs = experimentImportRows.stream()
//                .filter(experimentObservation -> StringUtils.isBlank(experimentObservation.getObsUnitID()))
//                .map(ExperimentObservation::getGid)
//                .distinct()
//                .collect(Collectors.toList());
//
//        try {
//            existingGermplasms.addAll(this.getGermplasmByAccessionNumber(uniqueGermplasmGIDs, program.getId()));
//        } catch (ApiException e) {
//            log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
//            throw new InternalServerException(e.toString(), e);
//        }
//
//        existingGermplasms.forEach(existingGermplasm -> {
//            BrAPIExternalReference xref = Utilities.getExternalReference(existingGermplasm.getExternalReferences(), String.format("%s", BRAPI_REFERENCE_SOURCE))
//                    .orElseThrow(() -> new IllegalStateException("External references wasn't found for germplasm (dbid): " + existingGermplasm.getGermplasmDbId()));
//            existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm, UUID.fromString(xref.getReferenceId())));
//        });
//        return existingGermplasmByGID;
//    }

    // TODO: used by expunit workflow
    public Map<String, PendingImportObject<BrAPIGermplasm>> mapGermplasmByOUId(
            String unitId,
            BrAPIObservationUnit unit,
            Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByName,
            Map<String, PendingImportObject<BrAPIGermplasm>> germplasmByOUId) {
        String gid = unit.getAdditionalInfo().getAsJsonObject().get(BrAPIAdditionalInfoFields.GID).getAsString();
        germplasmByOUId.put(unitId, germplasmByName.get(gid));

        return germplasmByOUId;
    }
}
