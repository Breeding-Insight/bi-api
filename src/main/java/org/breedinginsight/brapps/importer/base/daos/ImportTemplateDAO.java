package org.breedinginsight.brapps.importer.base.daos;

import org.breedinginsight.dao.db.tables.daos.ImporterTemplateDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;

public class ImportTemplateDAO extends ImporterTemplateDao {

    private DSLContext dsl;

    @Inject
    public ImportTemplateDAO(Configuration config, DSLContext dsl) {
        super(config);
        this.dsl = dsl;
    }
}
