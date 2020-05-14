package org.breedinginsight.api.auth;

import io.micronaut.security.authentication.UserDetails;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.UUID;

@Getter
@Setter
public class BiUserDetails extends UserDetails {

    private UUID id;

    public BiUserDetails(String username, Collection<String> roles) {
        super(username, roles);
    }

    public BiUserDetails(String username, Collection<String> roles, UUID id) {
        super(username, roles);
        this.id = id;
    }
}
