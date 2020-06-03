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

package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.SystemRoleDao;
import org.breedinginsight.dao.db.tables.pojos.SystemRoleEntity;
import org.breedinginsight.model.SystemRole;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class SystemRoleService {

    @Inject
    private SystemRoleDao dao;

    public List<SystemRole> getAll() {
        List<SystemRoleEntity> roleEntities = dao.findAll();
        List<SystemRole> roles =roleEntities.stream().map(roleEntity -> new SystemRole(roleEntity))
                .collect(Collectors.toList());

        return roles;
    }

    public Optional<SystemRole> getById(UUID roleId) {
        SystemRoleEntity role = dao.fetchOneById(roleId);

        if (role == null) {
            return Optional.empty();
        }

        return Optional.of(new SystemRole(role));
    }

    public List<SystemRole> getRolesByIds(List<UUID> roleIds) {
        List<SystemRoleEntity> roleEntities = dao.fetchById(roleIds.stream().toArray(UUID[]::new));
        List<SystemRole> roles =roleEntities.stream().map(roleEntity -> new SystemRole(roleEntity))
                .collect(Collectors.toList());

        return roles;
    }
}
