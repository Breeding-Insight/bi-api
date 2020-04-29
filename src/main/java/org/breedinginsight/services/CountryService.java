package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.dao.db.tables.daos.CountryDao;
import org.breedinginsight.dao.db.tables.pojos.CountryEntity;
import org.breedinginsight.model.Country;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Singleton
public class CountryService {

    @Inject
    private CountryDao dao;

    public List<Country> getAll() {
        List<CountryEntity> countryEntities = dao.findAll();

        List<Country> countries = new ArrayList<>();
        for (CountryEntity countryEntity: countryEntities){
            countries.add(new Country(countryEntity));
        }
        return countries;
    }

    public Optional<Country> getById(UUID countryId) {
        CountryEntity country = dao.fetchOneById(countryId);

        if (country == null) {
            return Optional.empty();
        }

        return Optional.of(new Country(country));
    }

    public boolean exists(UUID countryId){
        return dao.existsById(countryId);
    }

}
