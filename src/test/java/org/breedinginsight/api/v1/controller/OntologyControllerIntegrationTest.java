package org.breedinginsight.api.v1.controller;

import io.kowalski.fannypack.FannyPack;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.annotation.MicronautTest;
import org.breedinginsight.BrAPITest;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OntologyControllerIntegrationTest extends BrAPITest {

    private FannyPack fp;

    @Inject
    @Client("/${micronaut.bi.api.version}")
    private RxHttpClient client;

    @AfterAll
    public void finish() { super.stopContainers(); }

    @BeforeAll
    void setup() throws Exception {
        // Create two programs with fanny pack
    }

    @Test
    @Order(1)
    void getAllProgramsNoPrograms() {

    }

    @Test
    @Order(2)
    void addSharedPrograms() {

    }

    @Test
    @Order(3)
    void getAllProgramsSharedPrograms() {

    }

    @Test
    @Order(4)
    void revokeProgram() {

    }
}
