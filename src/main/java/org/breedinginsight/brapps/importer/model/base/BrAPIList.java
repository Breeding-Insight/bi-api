package org.breedinginsight.brapps.importer.model.base;

import lombok.Getter;
import lombok.Setter;
import org.brapi.v2.model.BrAPIExternalReference;
import org.brapi.v2.model.core.BrAPIListTypes;
import org.brapi.v2.model.core.request.BrAPIListNewRequest;
import org.breedinginsight.model.Program;

import java.util.List;

@Getter
@Setter
public class BrAPIList implements BrAPIObject {
    private String listName;
    private String listDescription;
    private BrAPIListTypes listType;
    private List<ExternalReference> externalReferences;
}
