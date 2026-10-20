package org.breedinginsight.model;

import lombok.*;
import org.brapi.v2.model.geno.BrAPICallSet;
import org.brapi.v2.model.germ.BrAPIGermplasm;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GermplasmGenotype {
    private BrAPIGermplasm germplasm;
    private Map<String, BrAPICallSet> callSets;
    // Calls are fetched a page at a time via GenotypeCallsPage when visualization is enabled
    private boolean callsEnabled;
}
