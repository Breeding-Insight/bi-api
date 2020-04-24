package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.EnvironmentTypeDao;
import org.breedinginsight.dao.db.tables.pojos.EnvironmentTypeEntity;
import org.breedinginsight.model.EnvironmentType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class EnvironmentTypeService {

    @Inject
    private EnvironmentTypeDao dao;

    public List<EnvironmentType> getAll() {
        List<EnvironmentTypeEntity> environmentEntities = dao.findAll();

        List<EnvironmentType> environments = new ArrayList<>();
        for (EnvironmentTypeEntity environmentEntity: environmentEntities){
            environments.add(new EnvironmentType(environmentEntity));
        }
        return environments;
    }

    public Optional<EnvironmentType> getById(UUID environmentId) {
        EnvironmentTypeEntity environment = dao.fetchOneById(environmentId);

        if (environment == null) {
            return Optional.empty();
        }

        return Optional.of(new EnvironmentType(environment));
    }

    public boolean exists(UUID environmentId){
        return dao.existsById(environmentId);
    }
}
