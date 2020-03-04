package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.RoleDao;
import org.breedinginsight.dao.db.tables.pojos.BiUserEntity;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;
import org.breedinginsight.daos.ProgramDao;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
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

        RoleEntity role = dao.fetchOneById(roleId);

        if (role == null) {
            throw new DoesNotExistException("UUID for role does not exist");
        }

        return role;
    }

}
