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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.utils.DefaultSecurityService;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramUser;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class SecurityService extends DefaultSecurityService {

    ObjectMapper objectMapper;
    ProgramDao programDao;

    @Inject
    public SecurityService(ObjectMapper objectMapper, ProgramDao programDao) {
        this.objectMapper = objectMapper;
        this.programDao = programDao;
    }

    public AuthenticatedUser getUser() {
        Optional<Authentication> optionalAuthentication = super.getAuthentication();
        if (optionalAuthentication.isPresent()) {
            Authentication authentication = optionalAuthentication.get();
            if (authentication.getAttributes() != null) {
                Object jwtId = authentication.getAttributes().get("id");
                UUID id = jwtId != null ? UUID.fromString(jwtId.toString()) : null;
                Object jwtRoles = authentication.getAttributes().get("roles");

                List<ProgramUser> jwtProgramRoles;
                try {
                    jwtProgramRoles = Arrays.asList(objectMapper.readValue(
                            authentication.getAttributes().get("programRoles").toString(), ProgramUser[].class));
                } catch (JsonProcessingException e) {
                    throw new HttpServerException("Unable to read program roles from the claims");
                }

                List<String> roles = (List<String>) jwtRoles;
                AuthenticatedUser authenticatedUser = new AuthenticatedUser(authentication.getName(), roles, id, jwtProgramRoles);
                return authenticatedUser;
            }
        }

        throw new HttpStatusException(HttpStatus.UNAUTHORIZED, null);
    }

    public List<UUID> getEnrolledProgramIds(AuthenticatedUser actingUser) {
        if (actingUser.getRoles().contains(ProgramSecuredRole.SYSTEM_ADMIN.toString())){
            return programDao.findAll().stream().map(ProgramEntity::getId).collect(Collectors.toList());
        }

        return actingUser.getProgramRoles().stream()
                .map(ProgramUser::getProgramId).collect(Collectors.toList());
    }

    public boolean canUpdateUser(AuthenticatedUser authenticatedUser, UUID targetUserId) {

        if (authenticatedUser.getRoles().contains(ProgramSecuredRole.SYSTEM_ADMIN.toString())) {
            return true;
        }

        if (!authenticatedUser.getId().equals(targetUserId)) {
            return true;
        }
        return false;
    }
}
