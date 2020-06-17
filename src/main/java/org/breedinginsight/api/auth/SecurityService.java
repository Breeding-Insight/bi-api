/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
