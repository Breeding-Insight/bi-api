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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.security.token.jwt.generator.JwtGeneratorConfiguration;

import javax.inject.Named;

@ConfigurationProperties("micronaut.security.api-token.jwt.generator")
@Named("apiTokenConfig")
@Secondary
public class ApiTokenJwtGeneratorConfigurationProperties implements JwtGeneratorConfiguration {
    public static final String PREFIX = "micronaut.security.api-token.jwt.generator";
    public static final Integer DEFAULT_EXPIRATION = 3600;
    private Integer refreshTokenExpiration = null;
    private Integer accessTokenExpiration;

    public ApiTokenJwtGeneratorConfigurationProperties() {
        this.accessTokenExpiration = DEFAULT_EXPIRATION;
    }

    public Integer getRefreshTokenExpiration() {
        return this.refreshTokenExpiration;
    }

    public Integer getAccessTokenExpiration() {
        return this.accessTokenExpiration;
    }

    public void setRefreshTokenExpiration(Integer refreshTokenExpiration) {
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public void setAccessTokenExpiration(Integer accessTokenExpiration) {
        if (accessTokenExpiration != null) {
            this.accessTokenExpiration = accessTokenExpiration;
        }

    }
}