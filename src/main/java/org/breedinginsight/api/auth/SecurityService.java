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
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.daos.UserDAO;
import org.breedinginsight.model.User;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class SecurityService extends DefaultSecurityService {

    private ProgramDao programDao;
    private UserDAO userDAO;
    private Provider<ActingUserProvider> actingUserProvider;

    @Inject
    public SecurityService(ProgramDao programDao, UserDAO userDAO,
                           Provider<ActingUserProvider> actingUserProvider) {
        this.programDao = programDao;
        this.userDAO = userDAO;
        this.actingUserProvider = actingUserProvider;
    }

    public AuthenticatedUser getUser() {

        // If our acting user has already been set, don't query them again
        if (actingUserProvider.get().getActingUser() != null) {
            return actingUserProvider.get().getActingUser();
        }

        Optional<Authentication> optionalAuthentication = super.getAuthentication();
        if (optionalAuthentication.isPresent()) {
            Authentication authentication = optionalAuthentication.get();
            if (authentication.getAttributes() != null) {
                Object userId = authentication.getAttributes().get("id");
                UUID id;
                if (userId != null) {
                    id = UUID.fromString(userId.toString());
                    Optional<User> optionalUser = userDAO.getUser(id);
                    if (optionalUser.isPresent()) {
                        User user = optionalUser.get();
                        List<String> systemRoles = user.getSystemRoles().stream()
                                .map(systemRole -> systemRole.getDomain()).collect(Collectors.toList());
                        AuthenticatedUser authenticatedUser = new AuthenticatedUser(user.getName(),
                                systemRoles, id, user.getProgramRoles());
                        actingUserProvider.get().setActingUser(authenticatedUser);
                        return authenticatedUser;
                    }
                }
            }
        }

        throw new HttpStatusException(HttpStatus.UNAUTHORIZED, null);
    }

    public List<UUID> getEnrolledProgramIds(AuthenticatedUser actingUser) {
        if (actingUser.getRoles().contains(ProgramSecuredRole.SYSTEM_ADMIN.toString())){
            return programDao.findAll().stream().map(ProgramEntity::getId).collect(Collectors.toList());
        }

        return actingUser.getProgramRoles().stream()
                .map(programUser -> programUser.getProgram().getId()).collect(Collectors.toList());
    }

    public boolean canUpdateUserRoles(AuthenticatedUser actingUser, UUID targetUserId) {
        // Admins can update their own program roles, others cannot
        if (actingUser.getRoles().contains(ProgramSecuredRole.SYSTEM_ADMIN.toString())) {
            return true;
        }

        if (!actingUser.getId().equals(targetUserId)) {
            return true;
        }
        return false;
    }

    public boolean canUpdateUser(AuthenticatedUser actingUser, UUID targetUserId) {
        // Only admins and self can update user info
        if (actingUser.getRoles().contains(ProgramSecuredRole.SYSTEM_ADMIN.toString())) {
            return true;
        }

        if (actingUser.getId().equals(targetUserId)) {
            return true;
        }
        return false;
    }
}
