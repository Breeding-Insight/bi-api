package org.breedinginsight.api.model.v1.request;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import org.breedinginsight.api.model.v1.validators.ProgramUserValid;
import org.breedinginsight.api.model.v1.validators.UserIdValid;
import org.breedinginsight.model.Role;
import org.breedinginsight.model.User;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

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
