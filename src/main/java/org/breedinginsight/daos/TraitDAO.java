package org.breedinginsight.daos;

import lombok.SneakyThrows;
import org.brapi.v2.phenotyping.model.BrApiTrait;
import org.breedinginsight.dao.db.tables.daos.TraitDao;
import org.breedinginsight.model.BrAPIProvider;
import org.breedinginsight.model.BrAPiClientType;
import org.breedinginsight.model.Trait;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;

@Singleton
public class TraitDAO extends TraitDao {

    private DSLContext dsl;
    @Inject
    BrAPIProvider brAPIProvider;

    @Inject
    public TraitDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    @SneakyThrows
    public List<Trait> getTraitsFull() {
        brAPIProvider.getTraitsAPI(BrAPiClientType.PHENO).getTraits();
        return null;
    }

    private List<BrApiTrait> getTraitsBrAPI() {
        return null;
    }

    public Trait getTraitFull() {
        return null;
    }

    private Trait getTraitBrAPI(UUID traitId) {
        return null;
    }

    public Trait createTrait(Trait trait) {
        return null;
    }

    private Trait createTraitBrAPI(Trait trait) {
        return null;
    }

    private Trait updateTraitBrAPI(Trait trait) {
        // Get BrAPI trait by external id

        // Update trait by db id
        return null;
    }
}
