package org.breedinginsight.api.auth;

import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.utils.DefaultSecurityService;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class BiSecurityService extends DefaultSecurityService {

    public UUID getId() {
         Optional<Authentication> optionalAuthentication = super.getAuthentication();
         if (optionalAuthentication.isPresent()){
             Authentication authentication = optionalAuthentication.get();
             if (authentication.getAttributes() != null){
                 Object id = authentication.getAttributes().get("id");
                 if (id != null){
                     return UUID.fromString(id.toString());
                 }
             }
         }

         return null;
    }
}
