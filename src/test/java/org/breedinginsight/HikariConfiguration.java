/*
 * See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
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
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;

import javax.inject.Singleton;

@Singleton
public class HikariConfiguration implements BeanInitializedEventListener<HikariConfig> {

    @Override
    public HikariConfig onInitialized(BeanInitializingEvent<HikariConfig> configurationEvent)
    {

        // Initialize container using TestContainers
        GenericContainer dbContainer = new GenericContainer<>("postgres:11.4")
                .withImagePullPolicy(PullPolicy.defaultPolicy())
                .withExposedPorts(5432)
                .withEnv("POSTGRES_DB", "bitest")
                .withEnv("POSTGRES_PASSWORD", "postgres")
                .waitingFor(Wait.forListeningPort());

        dbContainer.start();
        Integer containerPort = dbContainer.getMappedPort(5432);
        String containerIp = dbContainer.getContainerIpAddress();

        HikariConfig configuration = configurationEvent.getBean();

        configuration.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/bitest", containerIp, containerPort));

        return configuration;
    }

}

