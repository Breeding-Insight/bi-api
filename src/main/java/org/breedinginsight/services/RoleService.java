package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.RoleDao;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.model.Role;
import org.breedinginsight.services.exceptions.DoesNotExistException;

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

    public Role getById(UUID roleId) throws DoesNotExistException {

        Optional<Role> role = getByIdOptional(roleId);

        if (role.isEmpty()) {
            throw new DoesNotExistException("UUID for role does not exist");
        }

        return role.get();
    }

    public Optional<Role> getByIdOptional(UUID roleId) {
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

}
