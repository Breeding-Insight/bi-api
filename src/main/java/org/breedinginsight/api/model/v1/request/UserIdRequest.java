package org.breedinginsight.api.model.v1.request;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import org.breedinginsight.api.model.v1.validators.UserIdValid;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@UserIdValid
public class UserIdRequest {

    private UUID id;
    private String name;
    private String email;
}
