package org.breedinginsight.api.model.v1.request;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import org.breedinginsight.api.model.v1.validators.ProgramUserValid;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ProgramUserValid
public class ProgramUserRequest {

    private UUID id;

    private String name;

    private String email;

    private List<UUID> roleIds;
}
