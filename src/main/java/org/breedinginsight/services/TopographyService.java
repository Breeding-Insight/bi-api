package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.TopographyOptionDao;
import org.breedinginsight.dao.db.tables.pojos.TopographyOptionEntity;
import org.breedinginsight.model.Topography;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class TopographyService {

    @Inject
    private TopographyOptionDao dao;

    public List<Topography> getAll() {
        List<TopographyOptionEntity> topographyEntities = dao.findAll();

        List<Topography> topographies = new ArrayList<>();
        for (TopographyOptionEntity topographyEntity: topographyEntities){
            topographies.add(new Topography(topographyEntity));
        }
        return topographies;
    }

    public Optional<Topography> getById(UUID topographyId) {
        TopographyOptionEntity topography = dao.fetchOneById(topographyId);

        if (topography == null) {
            return Optional.empty();
        }

        return Optional.of(new Topography(topography));
    }

    public boolean exists(UUID topographyId){
        return dao.existsById(topographyId);
    }
}
