/*
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
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

package org.breedinginsight.api.v1.controller;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.response.ServerInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
@Controller("/${micronaut.bi.api.version}")
public class ServerInfoController {

    @Get("/server-info")
    @Produces(MediaType.APPLICATION_JSON)
    @Secured(SecurityRule.IS_ANONYMOUS)
    public ServerInfo getServerInfo() throws IOException {
        InputStream resourceAsStream = this.getClass()
                                           .getClassLoader()
                                           .getResourceAsStream("version.properties");

        Properties versionInfo = new Properties();
        versionInfo.load(resourceAsStream);

        return ServerInfo.builder().version(versionInfo.getProperty("version")).versionInfo(versionInfo.getProperty("versionInfo")).build();
    }
}
