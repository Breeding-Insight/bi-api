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

package org.breedinginsight.services.validators;

import io.kowalski.fannypack.FannyPack;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.SneakyThrows;
import org.breedinginsight.DatabaseTest;
import org.breedinginsight.dao.db.tables.daos.ProgramDao;
import org.breedinginsight.dao.db.tables.pojos.ProgramEntity;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.ProgramObservationLevel;
import org.breedinginsight.model.Scale;
import org.breedinginsight.model.Trait;
import org.jooq.DSLContext;
import org.junit.jupiter.api.*;

import javax.inject.Inject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TraitValidatorIntegrationTest extends DatabaseTest {

    private ProgramEntity validProgram;

    @Inject
    private DSLContext dsl;
    @Inject
    private ProgramDao programDao;
    @Inject
    private TraitValidatorService traitValidator;

    @AfterAll
    public void finish() { super.stopContainers(); }

    @BeforeAll
    public void setup() {

        // Insert our traits into the db
        var fp = FannyPack.fill("src/test/resources/sql/TraitControllerIntegrationTest.sql");

        // Insert program
        dsl.execute(fp.get("InsertProgram"));

        // Insert program observation level
        dsl.execute(fp.get("InsertProgramObservationLevel"));

        // Insert program ontology sql
        dsl.execute(fp.get("InsertProgramOntology"));

        // Insert Trait
        dsl.execute(fp.get("InsertMethod"));
        dsl.execute(fp.get("InsertScale"));
        dsl.execute(fp.get("InsertTrait"));

        // Retrieve our new data
        validProgram = programDao.findAll().get(0);
    }

    @Test
    @SneakyThrows
    public void duplicateTrait() {

        Trait trait1 = new Trait();
        trait1.setObservationVariableName("Test Trait");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        Method method1 = new Method();
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        List<Trait> duplicateTraits = traitValidator.checkDuplicateTraitsExistingByName(validProgram.getId(), List.of(trait1));

        assertEquals(1, duplicateTraits.size(), "Wrong number of duplicate traits by name returned");
    }

    @Test
    @SneakyThrows
    public void uniqueTraitSuccess() {

        Trait trait1 = new Trait();
        trait1.setObservationVariableName("Test Trait Unique");
        trait1.setProgramObservationLevel(ProgramObservationLevel.builder().name("Plant").build());
        Scale scale1 = new Scale();
        scale1.setScaleName("Test Scale");
        Method method1 = new Method();
        trait1.setScale(scale1);
        trait1.setMethod(method1);

        List<Trait> duplicateTraits = traitValidator.checkDuplicateTraitsExistingByName(validProgram.getId(), List.of(trait1));

        assertEquals(0, duplicateTraits.size(), "Wrong number of duplicate traits by name returned");
    }

}
