package org.breedinginsight.model;

import lombok.*;
import org.brapi.v2.model.geno.BrAPICall;
import org.brapi.v2.model.geno.BrAPICallSet;
import org.brapi.v2.model.geno.BrAPIVariant;
import org.brapi.v2.model.germ.BrAPIGermplasm;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GermplasmGenotype {
    private BrAPIGermplasm germplasm;
    private Map<String, BrAPICallSet> callSets;
    private Map<String, List<BrAPICall>> calls;
    private Map<String, BrAPIVariant> variants;
}
