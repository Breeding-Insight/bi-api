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

import com.nimbusds.jwt.JWTClaimsSet;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.token.config.TokenConfiguration;
import io.micronaut.security.token.jwt.generator.claims.ClaimsAudienceProvider;
import io.micronaut.security.token.jwt.generator.claims.JWTClaimsSetGenerator;
import io.micronaut.security.token.jwt.generator.claims.JwtIdGenerator;
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.Role;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Replaces(bean = JWTClaimsSetGenerator.class)
public class JWTClaimsGenerator extends JWTClaimsSetGenerator {


    public JWTClaimsGenerator(TokenConfiguration tokenConfiguration,
                              @Nullable JwtIdGenerator jwtIdGenerator,
                              @Nullable ClaimsAudienceProvider claimsAudienceProvider,
                              @Nullable ApplicationConfiguration applicationConfiguration) {
        super(tokenConfiguration, jwtIdGenerator, claimsAudienceProvider, applicationConfiguration);
    }

    @Override
    protected void populateWithUserDetails(JWTClaimsSet.Builder builder, UserDetails userDetails) {
        super.populateWithUserDetails(builder, userDetails);
        if (userDetails instanceof AuthenticatedUser) {
            builder.claim("id", ((AuthenticatedUser)userDetails).getId().toString());

            //TODO: Make a specific model or move this into the ProgramUser class
            List<Map<String, Object>> programRoles = new ArrayList<>();
            for (ProgramUser programUser : ((AuthenticatedUser)userDetails).getProgramRoles()) {
                Map<String, Object> roleEntry = new HashMap<>();
                roleEntry.put("programId", programUser.getProgramId().toString());
                List<Map<String, String>> roleArray = new ArrayList<>();
                for (Role role: programUser.getRoles()) {
                    Map<String, String> roleMap = new HashMap<>();
                    roleMap.put("id", role.getId().toString());
                    roleMap.put("domain", role.getDomain());
                    roleArray.add(roleMap);
                }
                roleEntry.put("roles", roleArray);
                programRoles.add(roleEntry);
            }
            builder.claim("programRoles", programRoles);
        }
    }
}
