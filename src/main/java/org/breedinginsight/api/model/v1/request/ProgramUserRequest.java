package org.breedinginsight.api.model.v1.request;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import org.breedinginsight.api.model.v1.validators.UserIdValid;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Introspected
public class ProgramUserRequest {

    @NotNull
    @UserIdValid
    private UserIdRequest user;

    @NotNull
    @Valid
    private List<RoleRequest> roles;
}
