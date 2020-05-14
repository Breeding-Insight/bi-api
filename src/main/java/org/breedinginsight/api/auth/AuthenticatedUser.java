package org.breedinginsight.api.auth;

import io.micronaut.security.authentication.UserDetails;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.UUID;

@Getter
@Setter
public class AuthenticatedUser extends UserDetails {

    private UUID id;

    public AuthenticatedUser(String username, Collection<String> roles) {
        super(username, roles);
    }

    public AuthenticatedUser(String username, Collection<String> roles, UUID id) {
        super(username, roles);
        this.id = id;
    }
}
