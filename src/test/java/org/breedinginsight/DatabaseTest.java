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

import io.micronaut.test.support.TestPropertyProvider;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
public class DatabaseTest implements TestPropertyProvider {

    private static final String dbName = "bitest";
    private static final String dbPassword = "postgres";

    @Getter
    private static GenericContainer dbContainer;

    @Getter
    private static Network network;

    @Getter
    private static GenericContainer redisContainer;

    @Getter
    private RedissonClient redisConnection;

//    @SneakyThrows
    public DatabaseTest() {
        if(network == null) {
            network = Network.newNetwork();
        }
        if(dbContainer == null) {
            dbContainer = new GenericContainer<>("postgres:11.4")
                    .withNetwork(network)
                    .withNetworkAliases("testdb")
                    .withImagePullPolicy(PullPolicy.defaultPolicy())
                    .withExposedPorts(5432)
                    .withEnv("POSTGRES_DB", dbName)
                    .withEnv("POSTGRES_PASSWORD", dbPassword)
                    .waitingFor(Wait.forLogMessage(".*LOG:  database system is ready to accept connections.*", 1)
                                    .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));
            dbContainer.start();
        }
        if(redisContainer == null) {
            redisContainer = new GenericContainer<>("redis")
                    .withNetwork(network)
                    .withNetworkAliases("redis")
                    .withImagePullPolicy(PullPolicy.defaultPolicy())
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());
            redisContainer.start();
        }

        Integer redisContainerPort = redisContainer.getMappedPort(6379);
        String redisContainerIp = redisContainer.getContainerIpAddress();
        Config redissonConfig = new Config();
        redissonConfig.useSingleServer().setAddress(String.format("redis://%s:%s", redisContainerIp, redisContainerPort));
        redisConnection = Redisson.create(redissonConfig);
    }

    @Nonnull
    @Override
    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        Integer containerPort = dbContainer.getMappedPort(5432);
        String containerIp = dbContainer.getContainerIpAddress();
        properties.put("datasources.default.url", String.format("jdbc:postgresql://%s:%s/%s", containerIp, containerPort, dbName));
        properties.put("micronaut.bi.api.run-scheduled-tasks", "false");
        properties.put("datasources.default.initialization-fail-timeout", "10");

        Integer redisContainerPort = redisContainer.getMappedPort(6379);
        String redisContainerIp = redisContainer.getContainerIpAddress();
        properties.put("redisson.single-server-config.address", String.format("redis://%s:%s", redisContainerIp, redisContainerPort));

        properties.put("micronaut.http.client.read-timeout", "1m");

        properties.put("gigwa.host", "http://gigwa:8080/");
        properties.put("gigwa.username", "gigwadmin");
        properties.put("gigwa.password", "nimda");

        properties.put("aws.accessKeyId", "test");
        properties.put("aws.secretKey", "test");
        properties.put("aws.s3.buckets.genotype.bucket", "test");

        return properties;
    }

    @SneakyThrows
    @AfterAll
    public void stopContainers() {
        if(redisContainer != null) {
            redisConnection.getKeys()
                           .flushall();
        }
        if(dbContainer != null) {
            resetDb(dbName);
            resetDb("postgres");
        }
    }

    private void resetDb(String name) {
        log.debug("resetting db: " + name);
        try (Connection con = DriverManager.
                getConnection(String.format("jdbc:postgresql://%s:%s/"+name,
                                            dbContainer.getContainerIpAddress(), dbContainer.getMappedPort(5432)),
                              "postgres", dbPassword)) {

            con.prepareStatement("drop schema public CASCADE").execute();
            con.prepareStatement("create schema public").execute();
        } catch (SQLException e) {
            log.error("Error during reset", e);
        }
    }
}
