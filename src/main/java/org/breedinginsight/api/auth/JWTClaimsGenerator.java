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
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.config.TokenConfiguration;
import io.micronaut.security.token.claims.ClaimsAudienceProvider;
import io.micronaut.security.token.jwt.generator.claims.JWTClaimsSetGenerator;
import io.micronaut.security.token.claims.JtiGenerator;

import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

@Singleton
@Replaces(bean = JWTClaimsSetGenerator.class)
public class JWTClaimsGenerator extends JWTClaimsSetGenerator {


    public JWTClaimsGenerator(TokenConfiguration tokenConfiguration,
                              @Nullable JtiGenerator jwtIdGenerator,
                              @Nullable ClaimsAudienceProvider claimsAudienceProvider,
                              @Nullable ApplicationConfiguration applicationConfiguration) {
        super(tokenConfiguration, jwtIdGenerator, claimsAudienceProvider, applicationConfiguration);
    }

    protected void populateWithUserDetails(JWTClaimsSet.Builder builder, Authentication userDetails) {
        // TODO: this is probably broken now.
        //super.populateWithUserDetails(builder, userDetails);
        if (userDetails instanceof AuthenticatedUser) {
            builder.claim("id", ((AuthenticatedUser)userDetails).getId().toString());
        }
    }
}
