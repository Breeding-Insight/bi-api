package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.RoleDao;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class RoleService {

    @Inject
    private RoleDao dao;

    public RoleEntity getDefaultRole() throws DoesNotExistException {

        List<RoleEntity> roles = dao.fetchByDomain("member");

        if (roles.size() != 1) {
            throw new DoesNotExistException("Role does not exist");
        }

        return roles.get(0);
    }

    public RoleEntity getById(UUID roleId) throws DoesNotExistException {

        Optional<RoleEntity> role = getByIdOptional(roleId);

        if (role.isEmpty()) {
            throw new DoesNotExistException("UUID for role does not exist");
        }

        return role.get();
    }


    public Optional<RoleEntity> getByIdOptional(UUID roleId) {

        // User has been authenticated against orcid, check they have a bi account.
        RoleEntity role = dao.fetchOneById(roleId);

        if (role == null) {
            return Optional.empty();
        }

        return Optional.of(role);
    }

}
