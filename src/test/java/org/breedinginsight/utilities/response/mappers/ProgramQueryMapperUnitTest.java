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

package org.breedinginsight.utilities.response.mappers;

import lombok.SneakyThrows;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.Species;
import org.breedinginsight.model.User;
import org.junit.jupiter.api.*;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProgramQueryMapperUnitTest {

    ProgramQueryMapper programQueryMapper;

    @BeforeAll
    @SneakyThrows
    public void setup() {
        programQueryMapper = new ProgramQueryMapper();
    }

    @Test
    public void testMappings() {
        Program program = Program.builder()
                .name("test")
                .abbreviation("t")
                .objective("Plant stuff")
                .documentationUrl("https://doc.com")
                .active(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .speciesId(UUID.randomUUID())
                .species(Species.builder().id(UUID.randomUUID()).commonName("species").build())
                .createdBy(UUID.randomUUID())
                .createdByUser(User.builder().name("tester").id(UUID.randomUUID()).build())
                .updatedBy(UUID.randomUUID())
                .updatedByUser(User.builder().name("tester").id(UUID.randomUUID()).build())

                .build();

        checkMapping(program.getName(), program, "name");
        checkMapping(program.getAbbreviation(), program, "abbreviation");
        checkMapping(program.getObjective(), program, "objective");
        checkMapping(program.getDocumentationUrl(), program, "documentationUrl");
        checkMapping(program.getActive(), program, "active");
        checkMapping(program.getCreatedAt(), program, "createdAt");
        checkMapping(program.getUpdatedAt(), program, "updatedAt");
        checkMapping(program.getSpeciesId(), program, "speciesId");
        checkMapping(program.getSpecies().getCommonName(), program, "speciesName");
        checkMapping(program.getCreatedByUser().getId(), program, "createdByUserId");
        checkMapping(program.getCreatedByUser().getName(), program, "createdByUserName");
        checkMapping(program.getUpdatedByUser().getId(), program, "updatedByUserId");
        checkMapping(program.getUpdatedByUser().getName(), program, "updatedByUserName");
    }

    private void checkMapping(Object field, Program program, String fieldName) {
        MapperEntry<Program> mapperEntry = programQueryMapper.getMapperEntry(fieldName);
        assertEquals(field, mapperEntry.getGetter().apply(program), "Wrong getter");
        assertEquals(true, mapperEntry.getFieldType() == field.getClass(), "Wrong class");
    }
}
