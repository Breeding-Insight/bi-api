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

    public BrAPIListNewRequest constructBrAPIList(Program program, String referenceSource) {
        BrAPIListNewRequest brapiList = new BrAPIListNewRequest();
        brapiList.setListName(constructGermplasmListName(listName, program));
        brapiList.setListDescription(this.listDescription);
        brapiList.listType(listType);
        // Set external reference
        BrAPIExternalReference reference = new BrAPIExternalReference();
        reference.setReferenceSource(String.format("%s/programs", referenceSource));
        reference.setReferenceID(program.getId().toString());
        brapiList.setExternalReferences(List.of(reference));
        return brapiList;
    }

    public static String constructGermplasmListName(String listName, Program program) {
        return String.format("%s [%s-germplasm]", listName, program.getName());
    }
}
