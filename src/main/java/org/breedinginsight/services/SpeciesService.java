package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.SpeciesDAO;
import org.breedinginsight.model.Species;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class SpeciesService {

    @Inject
    private SpeciesDAO dao;

    public List<Species> getAll() {
        List<SpeciesEntity> speciesEntities = dao.findAll();

        List<Species> species = new ArrayList<>();
        for (SpeciesEntity speciesEntity: speciesEntities){
            species.add(new Species(speciesEntity));
        }

        return species;
    }

    public Species getById(UUID speciesId) throws DoesNotExistException{

        Optional<Species> species = getByIdOptional(speciesId);

        if (species.isEmpty()){
            throw new DoesNotExistException("Species does not exist");
        }

        return species.get();
    }

    public Optional<Species> getByIdOptional(UUID speciesId) {

        SpeciesEntity species = dao.fetchOneById(speciesId);

        if (species == null) {
            return Optional.empty();
        }

        return Optional.of(new Species(species));
    }

    public boolean exists(UUID speciesId){
        return dao.existsById(speciesId);
    }
}
