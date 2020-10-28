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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.annotation.MicronautTest;
import io.reactivex.Flowable;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static io.micronaut.http.HttpRequest.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class ServerInfoControllerIntegrationTest {
    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @Test
    public void getVersionInfo() throws IOException {
        Flowable<HttpResponse<String>> call = client.exchange(GET("/serverInfo"), String.class);

        HttpResponse<String> response = call.blockingFirst();
        assertEquals(HttpStatus.OK, response.getStatus());

        Properties versionInfoProps = new Properties();
        versionInfoProps.load(new FileInputStream("src/main/resources/version.properties"));

        JsonObject serverInfo = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(versionInfoProps.get("version"), serverInfo.get("version").getAsString(), "wrong version name");
        assertEquals(versionInfoProps.get("versionInfo"), serverInfo.get("versionInfo").getAsString(), "wrong version info URL");
    }
}
