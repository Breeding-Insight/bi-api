package org.breedinginsight.api.auth;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.utils.DefaultSecurityService;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class SecurityService extends DefaultSecurityService {

    public AuthenticatedUser getUser() {
        Optional<Authentication> optionalAuthentication = super.getAuthentication();
        if (optionalAuthentication.isPresent()) {
            Authentication authentication = optionalAuthentication.get();
            if (authentication.getAttributes() != null) {
                Object jwtId = authentication.getAttributes().get("id");
                UUID id = jwtId != null ? UUID.fromString(jwtId.toString()) : null;
                Object jwtRoles = authentication.getAttributes().get("roles");
                List<String> roles = (List<String>) jwtRoles;
                AuthenticatedUser authenticatedUser = new AuthenticatedUser(authentication.getName(), roles, id);
                return authenticatedUser;
            }
        }

        throw new HttpStatusException(HttpStatus.UNAUTHORIZED, null);
    }
}
