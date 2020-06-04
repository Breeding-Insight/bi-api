package org.breedinginsight.services.brapi;

import org.brapi.v2.core.model.BrApiExternalReference;
import org.brapi.v2.phenotyping.model.BrApiVariable;

import java.util.*;

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
}
