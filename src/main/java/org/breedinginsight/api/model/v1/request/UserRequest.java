package org.breedinginsight.api.model.v1.request;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import org.breedinginsight.model.SystemRole;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Introspected
public class UserRequest {

    @NotBlank
    private String name;

    @NotNull
    @Email
    private String email;

    private List<SystemRole> systemRoles;
}
