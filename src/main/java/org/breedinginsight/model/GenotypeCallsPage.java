package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.brapi.v2.model.geno.BrAPICall;
import org.brapi.v2.model.geno.BrAPIVariant;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenotypeCallsPage {
    private String callSetDbId;
    private List<BrAPICall> calls;
    // Only the variants referenced by this page's calls
    private Map<String, BrAPIVariant> variants;

    // Paging details are returned in the response metadata
    @JsonIgnore
    private int page;
    @JsonIgnore
    private int pageSize;
    @JsonIgnore
    private int totalCount;
}
