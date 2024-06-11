package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.read;

import io.micronaut.context.ApplicationContext;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingStudy;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;

public class BrAPIStudyReadWorkflowInitialization extends BrAPIReadWorkflowInitialization<BrAPIStudy> {

    public BrAPIStudyReadWorkflowInitialization(ExpUnitMiddlewareContext context) {
        super(context);
    }

    /**
     * Get the BrAPI entity being acted on based on the provided ExpUnitMiddlewareContext.
     *
     * @param context The ExpUnitMiddlewareContext providing information about the entity.
     * @return The ExperimentImportEntity representing the BrAPI entity being acted on.
     */
    @Override
    public ExperimentImportEntity<BrAPIStudy> getEntity(ExpUnitMiddlewareContext context) {
        try (ApplicationContext appContext = ApplicationContext.run()) {
            StudyService studyService = appContext.getBean(StudyService.class);
            BrAPIStudyDAO brAPIStudyDAO = appContext.getBean(BrAPIStudyDAO.class);
            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);

            return new PendingStudy(context, studyService, brAPIStudyDAO, experimentUtilities);
        }
    }
}
