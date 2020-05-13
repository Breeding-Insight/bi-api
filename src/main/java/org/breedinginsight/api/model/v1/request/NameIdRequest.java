package org.breedinginsight.api.model.v1.request;

import io.micronaut.core.annotation.Introspected;
import lombok.*;

import javax.validation.constraints.NotBlank;
import java.util.UUID;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Introspected
public class NameIdRequest {
    @NotBlank
    private UUID id;
    private String name;
}
