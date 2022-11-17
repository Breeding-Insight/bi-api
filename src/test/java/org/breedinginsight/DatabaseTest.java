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
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;

import javax.annotation.Nonnull;
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
    private static Network network;

    @Getter
    private GenericContainer redisContainer;

    @Getter
    private RedissonClient redisConnection;

    @Getter
    private static GenericContainer gigwa;

    @Getter
    private static GenericContainer mongo;

    @Getter
    private static LocalStackContainer localStackContainer;

    @SneakyThrows
    public DatabaseTest() {
        if(network == null) {
            network = Network.newNetwork();
        }
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

        if(mongo == null) {
            mongo = new GenericContainer<>("mongo:4.2.21")
                    .withNetwork(network)
                    .withNetworkAliases("gigwa_db")
                    .withImagePullPolicy(PullPolicy.defaultPolicy())
                    .withExposedPorts(27017)
                    .withEnv("MONGO_INITDB_ROOT_USERNAME", "mongo")
                    .withEnv("MONGO_INITDB_ROOT_PASSWORD", "mongo")
                    .withCommand("--profile 0 --slowms 60000 --storageEngine wiredTiger --wiredTigerCollectionBlockCompressor=zstd --directoryperdb --quiet");
            mongo.start();
        }

        if(gigwa == null) {
            gigwa = new GenericContainer<>("breedinginsight/gigwa:develop")
                    .withNetwork(network)
                    .withNetworkAliases("gigwa")
                    .withImagePullPolicy(PullPolicy.defaultPolicy())
                    .withExposedPorts(8080)
                    .withEnv("MONGO_IP", "gigwa_db")
                    .withEnv("MONGO_PORT", "27017")
                    .withEnv("MONGO_INITDB_ROOT_USERNAME", "mongo")
                    .withEnv("MONGO_INITDB_ROOT_PASSWORD", "mongo")
                    .waitingFor(
                            Wait.forHttp("/gigwa")
                                .forStatusCode(200)
                                .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));
            gigwa.start();
        }

        if(localStackContainer == null) {
            localStackContainer = new LocalStackContainer(DockerImageName.parse("localstack/localstack")
                                                                         .withTag("1.2.0"))
                    .withServices(LocalStackContainer.Service.S3)
                    .withNetwork(network)
                    .withNetworkAliases("aws");
            localStackContainer.start();
        }

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

        properties.put("gigwa.host", "http://"+gigwa.getContainerIpAddress()+":"+gigwa.getMappedPort(8080)+"/");
        properties.put("gigwa.username", "gigwadmin");
        properties.put("gigwa.password", "nimda");

        properties.put("aws.region", localStackContainer.getRegion());
        properties.put("aws.accessKeyId", localStackContainer.getAccessKey());
        properties.put("aws.secretKey", localStackContainer.getSecretKey());
        properties.put("aws.s3.buckets.geno.bucket", "test");
        properties.put("aws.s3.endpoint", String.valueOf(localStackContainer.getEndpointOverride(LocalStackContainer.Service.S3)));

        return properties;
    }

    @SneakyThrows
    @AfterAll
    public void stopContainers() {
        redisContainer.stop();
        dbContainer.stop();
        network.close();
//        gigwa.stop();
//        mongo.stop();
//        localStackContainer.stop();
    }
}
