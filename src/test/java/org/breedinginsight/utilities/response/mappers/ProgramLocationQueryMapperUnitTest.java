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
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.model.User;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProgramLocationQueryMapperUnitTest {

    ProgramLocationQueryMapper programLocationQueryMapper;

    @BeforeAll
    @SneakyThrows
    public void setup() {
        programLocationQueryMapper = new ProgramLocationQueryMapper();
    }
    // Getters and types map properly
    @Test
    @SneakyThrows
    public void testMappings() {

        ProgramLocation programLocation = ProgramLocation.builder()
                .name("place1")
                .slope(BigDecimal.valueOf(7.6))
                .abbreviation("pla")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdByUser(User.builder().id(UUID.randomUUID()).name("user1").build())
                .updatedByUser(User.builder().id(UUID.randomUUID()).name("user2").build())
                .build();

        assertEquals(programLocation.getName(), programLocationQueryMapper.getField("name").apply(programLocation), "Wrong getter");
        assertEquals(programLocation.getSlope(), programLocationQueryMapper.getField("slope").apply(programLocation), "Wrong getter");
        assertEquals(programLocation.getAbbreviation(), programLocationQueryMapper.getField("abbreviation").apply(programLocation), "Wrong getter");
        assertEquals(programLocation.getCreatedAt(), programLocationQueryMapper.getField("createdAt").apply(programLocation), "Wrong getter");
        assertEquals(programLocation.getUpdatedAt(), programLocationQueryMapper.getField("updatedAt").apply(programLocation), "Wrong getter");
        assertEquals(programLocation.getCreatedByUser().getName(), programLocationQueryMapper.getField("createdByUserName").apply(programLocation), "Wrong getter");
        assertEquals(programLocation.getCreatedByUser().getId(), programLocationQueryMapper.getField("createdByUserId").apply(programLocation), "Wrong getter");
        assertEquals(programLocation.getUpdatedByUser().getName(), programLocationQueryMapper.getField("updatedByUserName").apply(programLocation), "Wrong getter");
        assertEquals(programLocation.getUpdatedByUser().getId(), programLocationQueryMapper.getField("updatedByUserId").apply(programLocation), "Wrong getter");
    }

}
