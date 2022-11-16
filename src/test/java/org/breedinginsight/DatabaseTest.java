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
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;

import javax.annotation.Nonnull;
import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DatabaseTest implements TestPropertyProvider {

    private static final String dbName = "bitest";
    private static final String dbPassword = "postgres";

    @Getter
    private GenericContainer dbContainer;

    @Getter
    private Network network;

    @Getter
    private GenericContainer redisContainer;

    @Getter
    private RedissonClient redisConnection;

    @Getter
    private DockerComposeContainer gigwa;

    @SneakyThrows
    public DatabaseTest() {
        network = Network.newNetwork();
        dbContainer = new GenericContainer<>("postgres:11.4")
                .withNetwork(network)
                .withNetworkAliases("testdb")
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withExposedPorts(5432)
                .withEnv("POSTGRES_DB", dbName)
                .withEnv("POSTGRES_PASSWORD", dbPassword)
                .waitingFor(Wait.forLogMessage(".*LOG:  database system is ready to accept connections.*", 1).withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));
        dbContainer.start();
        redisContainer = new GenericContainer<>("redis")
                .withNetwork(network)
                .withNetworkAliases("redis")
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());
        redisContainer.start();

        Integer redisContainerPort = redisContainer.getMappedPort(6379);
        String redisContainerIp = redisContainer.getContainerIpAddress();
        Config redissonConfig = new Config();
        redissonConfig.useSingleServer().setAddress(String.format("redis://%s:%s", redisContainerIp, redisContainerPort));
        redisConnection = Redisson.create(redissonConfig);

        gigwa = new DockerComposeContainer(new File("src/test/resources/gigwa-docker.yml"))
                .withExposedService("tomcat", 8080)
                .waitingFor("tomcat",
                            Wait.forHttp("/gigwa")
                                .forStatusCode(200)
                                .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));
        gigwa.start();
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

        properties.put("gigwa.host", "http://"+gigwa.getServiceHost("tomcat", 8080)+":"+gigwa.getServicePort("tomcat", 8080)+"/");
        properties.put("gigwa.username", "gigwadmin");
        properties.put("gigwa.password", "nimda");

        properties.put("aes.s3.buckets.geno.bucket", "TEST");
        properties.put("aes.accessKeyId", "TEST");
        properties.put("aes.secretKey", "TEST");

        return properties;
    }

    @SneakyThrows
    @AfterAll
    public void stopContainers() {
        redisContainer.stop();
        dbContainer.stop();
        network.close();
        gigwa.stop();
    }
}
