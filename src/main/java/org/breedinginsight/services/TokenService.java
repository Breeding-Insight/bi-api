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
package org.breedinginsight.services;

import io.micronaut.security.token.jwt.render.AccessRefreshToken;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.ApiAccessRefreshTokenGenerator;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.model.ApiToken;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Slf4j
@Singleton
public class TokenService {

    private ApiAccessRefreshTokenGenerator apiAccessRefreshTokenGenerator;

    @Inject
    public TokenService(ApiAccessRefreshTokenGenerator apiAccessRefreshTokenGenerator) {
        this.apiAccessRefreshTokenGenerator = apiAccessRefreshTokenGenerator;
    }

    public Optional<ApiToken> generateApiToken(AuthenticatedUser user) {

        Optional<AccessRefreshToken> tokenOptional = this.apiAccessRefreshTokenGenerator.generate(user);

        if (tokenOptional.isPresent()) {
            return Optional.of(ApiToken.builder().accessToken(tokenOptional.get().getAccessToken()).build());
        }
        else {
            return Optional.empty();
        }
    }

}
