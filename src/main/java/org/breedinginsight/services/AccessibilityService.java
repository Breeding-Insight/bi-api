package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.AccessibilityOptionDao;
import org.breedinginsight.dao.db.tables.pojos.AccessibilityOptionEntity;
import org.breedinginsight.model.Accessibility;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class AccessibilityService {

    @Inject
    private AccessibilityOptionDao dao;

    public List<Accessibility> getAll() {
        List<AccessibilityOptionEntity> accessibilityEntities = dao.findAll();

        List<Accessibility> accessibilities = new ArrayList<>();
        for (AccessibilityOptionEntity accessibilityEntity: accessibilityEntities){
            accessibilities.add(new Accessibility(accessibilityEntity));
        }
        return accessibilities;
    }

    public Optional<Accessibility> getById(UUID accessibilityId) {
        AccessibilityOptionEntity accessibility = dao.fetchOneById(accessibilityId);

        if (accessibility == null) {
            return Optional.empty();
        }

        return Optional.of(new Accessibility(accessibility));
    }

    public boolean exists(UUID accessibilityId){
        return dao.existsById(accessibilityId);
    }

}
