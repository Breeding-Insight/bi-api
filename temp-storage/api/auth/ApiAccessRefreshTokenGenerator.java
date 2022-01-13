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

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.token.generator.TokenGenerator;
import io.micronaut.security.token.jwt.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.jwt.generator.JwtGeneratorConfiguration;
import io.micronaut.security.token.jwt.generator.claims.ClaimsGenerator;
import io.micronaut.security.token.jwt.render.TokenRenderer;

import javax.inject.Named;

public class ApiAccessRefreshTokenGenerator extends AccessRefreshTokenGenerator {

    public ApiAccessRefreshTokenGenerator(@Named("apiTokenConfig") JwtGeneratorConfiguration jwtGeneratorConfiguration, TokenRenderer tokenRenderer, TokenGenerator tokenGenerator, ClaimsGenerator claimsGenerator, ApplicationEventPublisher eventPublisher) {
        super(jwtGeneratorConfiguration, tokenRenderer, tokenGenerator, claimsGenerator, eventPublisher);
    }

}
