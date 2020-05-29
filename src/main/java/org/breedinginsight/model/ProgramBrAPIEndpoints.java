package org.breedinginsight.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Optional;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProgramBrAPIEndpoints {
    Optional<String> coreUrl = Optional.empty();
    Optional<String> genoUrl = Optional.empty();
    Optional<String> phenoUrl = Optional.empty();
}
