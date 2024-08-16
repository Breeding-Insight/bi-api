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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.RoleDao;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.model.Role;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class RoleService {

    @Inject
    private RoleDao dao;

    public List<Role> getAll() {
        List<RoleEntity> roleEntities = dao.findAll();

        List<Role> roles = new ArrayList<>();
        for (RoleEntity roleEntity: roleEntities){
            roles.add(new Role(roleEntity));
        }
        return roles;
    }

    public Optional<Role> getById(UUID roleId) {
        RoleEntity role = dao.fetchOneById(roleId);

        if (role == null) {
            return Optional.empty();
        }

        return Optional.of(new Role(role));
    }

    public List<Role> getRolesByIds(List<UUID> roleIds) {
        List<RoleEntity> roleEntities = dao.fetchById(roleIds.stream().toArray(UUID[]::new));

        List<Role> roles = new ArrayList<>();
        for (RoleEntity roleEntity: roleEntities){
            roles.add(new Role(roleEntity));
        }
        return roles;
    }

    public Optional<Role> getRoleByDomain(String domain) {
        RoleEntity role = dao.fetchByDomain(domain).get(0);
        if (role == null) {
            return Optional.empty();
        }

        return Optional.of(new Role(role));
    }

}
