package org.breedinginsight.brapps.importer.services.processors.experiment.service;

import io.micronaut.http.server.exceptions.InternalServerException;
import org.apache.commons.lang3.StringUtils;
import org.brapi.client.v2.model.exceptions.ApiException;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.germ.BrAPIGermplasm;
import org.brapi.v2.model.pheno.BrAPIObservationUnit;
import org.breedinginsight.brapi.v2.constants.BrAPIAdditionalInfoFields;
import org.breedinginsight.brapps.importer.model.imports.experimentObservation.ExperimentObservation;
import org.breedinginsight.brapps.importer.model.response.ImportObjectState;
import org.breedinginsight.brapps.importer.model.response.PendingImportObject;
import org.breedinginsight.model.Program;
import org.breedinginsight.utilities.Utilities;

import java.util.*;
import java.util.stream.Collectors;

public class GermplasmService {
    // TODO: used by expunit workflow
    public Map<String, PendingImportObject<BrAPIGermplasm>> initializeGermplasmByGIDForExistingObservationUnits(
            Map<String, PendingImportObject<BrAPIObservationUnit>> unitByName,
            Program program) {
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();

        List<BrAPIGermplasm> existingGermplasms = new ArrayList<>();
        if(unitByName.size() > 0) {
            Set<String> germplasmDbIds = unitByName.values().stream().map(ou -> ou.getBrAPIObject().getGermplasmDbId()).collect(Collectors.toSet());
            try {
                existingGermplasms.addAll(brAPIGermplasmDAO.getGermplasmsByDBID(germplasmDbIds, program.getId()));
            } catch (ApiException e) {
                log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }

        existingGermplasms.forEach(existingGermplasm -> {
            BrAPIExternalReference xref = Utilities.getExternalReference(existingGermplasm.getExternalReferences(), String.format("%s", BRAPI_REFERENCE_SOURCE))
                    .orElseThrow(() -> new IllegalStateException("External references wasn't found for germplasm (dbid): " + existingGermplasm.getGermplasmDbId()));
            existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm, UUID.fromString(xref.getReferenceId())));
        });
        return existingGermplasmByGID;
    }

    // TODO: used by create worflow
    public Map<String, PendingImportObject<BrAPIGermplasm>> initializeExistingGermplasmByGID(Program program, List<ExperimentObservation> experimentImportRows) {
        Map<String, PendingImportObject<BrAPIGermplasm>> existingGermplasmByGID = new HashMap<>();

        List<BrAPIGermplasm> existingGermplasms = new ArrayList<>();
        if(observationUnitByNameNoScope.size() > 0) {
            Set<String> germplasmDbIds = observationUnitByNameNoScope.values().stream().map(ou -> ou.getBrAPIObject().getGermplasmDbId()).collect(Collectors.toSet());
            try {
                existingGermplasms.addAll(brAPIGermplasmDAO.getGermplasmsByDBID(germplasmDbIds, program.getId()));
            } catch (ApiException e) {
                log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
                throw new InternalServerException(e.toString(), e);
            }
        }

        List<String> uniqueGermplasmGIDs = experimentImportRows.stream()
                .filter(experimentObservation -> StringUtils.isBlank(experimentObservation.getObsUnitID()))
                .map(ExperimentObservation::getGid)
                .distinct()
                .collect(Collectors.toList());

        try {
            existingGermplasms.addAll(this.getGermplasmByAccessionNumber(uniqueGermplasmGIDs, program.getId()));
        } catch (ApiException e) {
            log.error("Error fetching germplasm: " + Utilities.generateApiExceptionLogMessage(e), e);
            throw new InternalServerException(e.toString(), e);
        }

        existingGermplasms.forEach(existingGermplasm -> {
            BrAPIExternalReference xref = Utilities.getExternalReference(existingGermplasm.getExternalReferences(), String.format("%s", BRAPI_REFERENCE_SOURCE))
                    .orElseThrow(() -> new IllegalStateException("External references wasn't found for germplasm (dbid): " + existingGermplasm.getGermplasmDbId()));
            existingGermplasmByGID.put(existingGermplasm.getAccessionNumber(), new PendingImportObject<>(ImportObjectState.EXISTING, existingGermplasm, UUID.fromString(xref.getReferenceId())));
        });
        return existingGermplasmByGID;
    }

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
