package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.update;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.pheno.BrAPIObservation;
import org.breedinginsight.brapi.v2.dao.BrAPIObservationDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingObservation;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.services.OntologyService;

@Slf4j
public class BrAPIObservationUpdate extends BrAPIUpdate<BrAPIObservation> {
    ExpUnitMiddlewareContext context;
    public BrAPIObservationUpdate(ExpUnitMiddlewareContext context) {

        this.context = context;
    }

    @Override
    public ExperimentImportEntity<BrAPIObservation> getEntity() {
        try (ApplicationContext appContext = ApplicationContext.run()) {
            BrAPIObservationDAO brAPIObservationDAO = appContext.getBean(BrAPIObservationDAO.class);
            OntologyService ontologyService = appContext.getBean(OntologyService.class);
            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);

            return new PendingObservation(context, brAPIObservationDAO, ontologyService, experimentUtilities);
        }
    }
}
