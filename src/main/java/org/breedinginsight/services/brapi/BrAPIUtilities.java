package org.breedinginsight.services.brapi;

import org.brapi.v2.core.model.BrApiExternalReference;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class BrAPIUtilities {

    public static Boolean isUUIDFormatted(String candidate) {
        // Checks for UUIDv4
        return candidate.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    public static Boolean hasMatchingExternalReference(List<BrApiExternalReference> externalReferences, UUID dbId) {

        for (BrApiExternalReference externalReference: externalReferences){
            if (isUUIDFormatted(externalReference.getReferenceID())) {

                UUID externalTraitId = UUID.fromString(externalReference.getReferenceID());
                if (dbId.equals(externalTraitId)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static <T> Optional<T> findMatchingBrAPIObject(List<BrApiExternalReference> externalReferences, Map<UUID, T> dbMap) {

        for (BrApiExternalReference externalReference: externalReferences){
            if (isUUIDFormatted(externalReference.getReferenceID())) {

                UUID externalTraitId = UUID.fromString(externalReference.getReferenceID());
                if (dbMap.containsKey(externalTraitId)) {
                    return Optional.of(dbMap.get(externalTraitId));
                }
            }
        }

        return Optional.empty();
    }
}
