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
import org.breedinginsight.model.ProgramUser;
import org.breedinginsight.model.Role;
import org.breedinginsight.model.User;
import org.junit.jupiter.api.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProgramUsersQueryMapperUnitTest {

    ProgramUserQueryMapper programUserQueryMapper;

    @BeforeAll
    @SneakyThrows
    public void setup() {
        programUserQueryMapper = new ProgramUserQueryMapper();
    }

    @Test
    public void testMappings() {

        ProgramUser programUser = ProgramUser.builder()
                .user(User.builder()
                        .name("test user")
                        .id(UUID.randomUUID())
                        .email("test@user.com")
                        .build())
                .createdByUser(User.builder().name("test user1").id(UUID.randomUUID()).build())
                .updatedByUser(User.builder().name("test user2").id(UUID.randomUUID()).build())
                .active(true)
                .roles(List.of(Role.builder().domain("test role").build(),
                        Role.builder().domain("test role1").build()))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        assertEquals(programUser.getUser().getName(),
                programUserQueryMapper.getField("name").apply(programUser), "Wrong getter");
        assertEquals(programUser.getUser().getEmail(),
                programUserQueryMapper.getField("email").apply(programUser), "Wrong getter");
        assertEquals(programUser.getRoles().stream().map(role -> role.getDomain()).collect(Collectors.toList()),
                programUserQueryMapper.getField("roles").apply(programUser), "Wrong getter");
        assertEquals(programUser.getActive(),
                programUserQueryMapper.getField("active").apply(programUser), "Wrong getter");
        assertEquals(programUser.getCreatedAt(),
                programUserQueryMapper.getField("createdAt").apply(programUser), "Wrong getter");
        assertEquals(programUser.getUpdatedAt(),
                programUserQueryMapper.getField("updatedAt").apply(programUser), "Wrong getter");
        assertEquals(programUser.getCreatedByUser().getId(),
                programUserQueryMapper.getField("createdByUserId").apply(programUser), "Wrong getter");
        assertEquals(programUser.getCreatedByUser().getName(),
                programUserQueryMapper.getField("createdByUserName").apply(programUser), "Wrong getter");
        assertEquals(programUser.getUpdatedByUser().getId(),
                programUserQueryMapper.getField("updatedByUserId").apply(programUser), "Wrong getter");
        assertEquals(programUser.getUpdatedByUser().getName(),
                programUserQueryMapper.getField("updatedByUserName").apply(programUser), "Wrong getter");
    }
}
