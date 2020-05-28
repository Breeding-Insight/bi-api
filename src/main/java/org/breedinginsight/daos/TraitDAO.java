package org.breedinginsight.daos;

import lombok.SneakyThrows;
import org.brapi.client.v2.BrAPIClient;
import org.brapi.client.v2.modules.core.ProgramsAPI;
import org.brapi.v2.phenotyping.model.BrApiTrait;
import org.breedinginsight.dao.db.tables.daos.TraitDao;
import org.breedinginsight.model.BrAPIClientProvider;
import org.breedinginsight.model.Trait;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.UUID;

@Singleton
public class TraitDAO extends TraitDao {

    private DSLContext dsl;
    @Inject
    ProgramsAPI programsAPI;
    @Inject
    BrAPIClientProvider brAPIClientProvider;

    @Inject
    public TraitDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }

    @SneakyThrows
    public List<Trait> getTraitsFull() {
        // Get the db traits
        //TODO: Inject this and pass BrAPIClient as injectable
        programsAPI.getPrograms();

        brAPIClientProvider.getProgramsAPI().getPrograms();

        /*ProgramsAPI programsAPI2 = new ProgramsAPI(phenoClient);
        programsAPI2.getPrograms();

        ProgramsAPI programsAPI3 = new ProgramsAPI(genoClient);
        programsAPI.getPrograms();*/

        // Get the brapi traits

        // TODO: Check what the brapi server is
        // Check if the brapi server supports variables endpoint

        // Process the brapi traits to the db traits

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
