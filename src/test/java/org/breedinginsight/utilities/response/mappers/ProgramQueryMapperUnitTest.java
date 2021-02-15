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
                .brapiUrl("http://www.test.com")
                .numUsers(5)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .speciesId(UUID.randomUUID())
                .species(Species.builder().id(UUID.randomUUID()).commonName("species").build())
                .createdBy(UUID.randomUUID())
                .createdByUser(User.builder().name("tester").id(UUID.randomUUID()).build())
                .updatedBy(UUID.randomUUID())
                .updatedByUser(User.builder().name("tester").id(UUID.randomUUID()).build())
                .build();

        assertEquals(program.getName(), programQueryMapper.getField("name").apply(program), "Wrong getter");
        assertEquals(program.getAbbreviation(), programQueryMapper.getField("abbreviation").apply(program), "Wrong getter");
        assertEquals(program.getObjective(), programQueryMapper.getField("objective").apply(program), "Wrong getter");
        assertEquals(program.getDocumentationUrl(), programQueryMapper.getField("documentationUrl").apply(program), "Wrong getter");
        assertEquals(program.getActive(), programQueryMapper.getField("active").apply(program), "Wrong getter");
        assertEquals(program.getBrapiUrl(), programQueryMapper.getField("brapiUrl").apply(program), "Wrong getter");
        assertEquals(program.getNumUsers(), programQueryMapper.getField("numUsers").apply(program), "Wrong getter");
        assertEquals(program.getCreatedAt(), programQueryMapper.getField("createdAt").apply(program), "Wrong getter");
        assertEquals(program.getUpdatedAt(), programQueryMapper.getField("updatedAt").apply(program), "Wrong getter");
        assertEquals(program.getSpeciesId(), programQueryMapper.getField("speciesId").apply(program), "Wrong getter");
        assertEquals(program.getSpecies().getCommonName(), programQueryMapper.getField("speciesName").apply(program), "Wrong getter");
        assertEquals(program.getCreatedByUser().getId(), programQueryMapper.getField("createdByUserId").apply(program), "Wrong getter");
        assertEquals(program.getCreatedByUser().getName(), programQueryMapper.getField("createdByUserName").apply(program), "Wrong getter");
        assertEquals(program.getUpdatedByUser().getId(), programQueryMapper.getField("updatedByUserId").apply(program), "Wrong getter");
        assertEquals(program.getUpdatedByUser().getName(), programQueryMapper.getField("updatedByUserName").apply(program), "Wrong getter");
    }

}
