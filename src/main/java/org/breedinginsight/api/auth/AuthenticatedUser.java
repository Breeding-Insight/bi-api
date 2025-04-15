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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.security.authentication.Authentication;
import lombok.Getter;
import lombok.Setter;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class AuthenticatedUser implements Authentication {

    private UUID id;
    private List<ProgramUser> programRoles;
    private String username;
    private Collection<String> roles;

    public AuthenticatedUser(String username, Collection<String> roles, UUID id, List<ProgramUser> programRoles) {
        this.username = username;
        this.roles = roles;
        this.id = id;
        this.programRoles = programRoles;
    }

    public ProgramUser extractProgramUser(UUID programId) throws DoesNotExistException {
        return this.programRoles.stream()
                .filter(pu -> programId.equals( pu.getProgramId() ) )
                .findFirst()
                .orElseThrow( () -> new DoesNotExistException( String.format("No program user found for program %s", this.id) ) );
    }

    @Override
    public @NonNull Map<String, Object> getAttributes() {
        // TODO: what is this?
        return Map.of();
    }

    @Override
    public String getName() {
        return this.username;
    }
}
