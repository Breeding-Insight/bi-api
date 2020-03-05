package org.breedinginsight.api.model.v1.request;

import io.micronaut.core.annotation.Introspected;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Introspected
public class ProgramRequest {
    @NotBlank
    private String name;
    private String abbreviation;
    private String objective;
    private String documentationUrl;
    @NotNull
    @Valid
    private SpeciesRequest species;
}
