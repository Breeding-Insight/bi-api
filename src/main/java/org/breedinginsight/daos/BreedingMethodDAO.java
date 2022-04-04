package org.breedinginsight.daos;

import org.breedinginsight.dao.db.tables.daos.BreedingMethodDao;
import org.breedinginsight.dao.db.tables.pojos.BreedingMethodEntity;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static org.breedinginsight.dao.db.tables.BreedingMethodTable.BREEDING_METHOD;

@Singleton
public class BreedingMethodDAO extends BreedingMethodDao {

    private DSLContext dsl;

    @Inject
    public BreedingMethodDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    public List<BreedingMethodEntity> findByNameOrAbbreviation(String nameOrAbbrev) {
        return dsl.select().from(BREEDING_METHOD)
                .where(BREEDING_METHOD.ABBREVIATION.equalIgnoreCase(nameOrAbbrev)
                        .or(BREEDING_METHOD.NAME.equalIgnoreCase(nameOrAbbrev)))
                .fetchInto(BreedingMethodEntity.class);
    }
}
