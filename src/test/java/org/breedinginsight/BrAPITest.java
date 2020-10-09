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

package org.breedinginsight;

import lombok.Getter;
import lombok.SneakyThrows;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

public class BrAPITest extends DatabaseTest {

    @Getter
    private static GenericContainer brapiContainer;
    @Getter
    private DSLContext brapiDsl;

    @SneakyThrows
    public BrAPITest() {
        brapiContainer = new GenericContainer<>("breedinginsight/brapi-java-server")
                .withNetwork(super.getDbContainer().getNetwork())
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withExposedPorts(8080)
                .withEnv("BRAPI_DB_SERVER",
                        String.format("%s:%s",
                                super.getDbContainer().getNetworkAliases().get(0),
                                5432))
                .withEnv("BRAPI_DB", "postgres")
                .withEnv("BRAPI_DB_USER", "postgres")
                .withEnv("BRAPI_DB_PASSWORD", "postgres")
                .withClasspathResourceMapping("brapi/properties/application.properties", "/home/brapi/properties/application.properties", BindMode.READ_ONLY)
                .waitingFor(Wait.forListeningPort());
        brapiContainer.start();

        // Get a dsl connection for the brapi db
        Connection con = DriverManager.
                getConnection(String.format("jdbc:postgresql://%s:%s/postgres",
                        super.getDbContainer().getContainerIpAddress(), super.getDbContainer().getMappedPort(5432)),
                        "postgres", "postgres");
        brapiDsl = DSL.using(con, SQLDialect.POSTGRES);
    }

    @Nonnull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();

        Integer containerPort = brapiContainer.getMappedPort(8080);
        String containerIp = brapiContainer.getContainerIpAddress();
        properties.put("brapi.server.core-url", String.format("http://%s:%s/", containerIp, containerPort));
        properties.put("brapi.server.geno-url", String.format("http://%s:%s/", containerIp, containerPort));
        properties.put("brapi.server.pheno-url", String.format("http://%s:%s/", containerIp, containerPort));
        properties.putAll(super.getProperties());

        return properties;
    }


}

