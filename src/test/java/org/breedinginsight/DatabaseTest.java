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

import com.zaxxer.hikari.HikariConfig;
import io.micronaut.test.support.TestPropertyProvider;
import lombok.Getter;
import lombok.SneakyThrows;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class DatabaseTest implements TestPropertyProvider {

    private static GenericContainer dbContainer;
    private final String dbName = "bitest";
    private final String dbPassword = "postgres";

    @SneakyThrows
    public DatabaseTest() {
        dbContainer = new GenericContainer<>("postgres:11.4")
                .withNetwork(Network.newNetwork())
                .withNetworkAliases("test-db")
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withExposedPorts(5432)
                .withEnv("POSTGRES_DB", dbName)
                .withEnv("POSTGRES_PASSWORD", dbPassword)
                .waitingFor(Wait.forListeningPort());
        dbContainer.start();
    }

    @Nonnull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        Integer containerPort = dbContainer.getMappedPort(5432);
        String containerIp = dbContainer.getContainerIpAddress();

        properties.put("datasources.default.url", String.format("jdbc:postgresql://%s:%s/%s", containerIp, containerPort, dbName));
        return properties;
    }

    public GenericContainer getDbContainer() {
        return dbContainer;
    }
}
