package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.action.create.misc;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.brapi.v2.model.core.BrAPIStudy;
import org.breedinginsight.brapi.v2.dao.BrAPIStudyDAO;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.ExperimentImportEntity;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.entity.PendingStudy;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.brapps.importer.services.processors.experiment.service.StudyService;

@Slf4j
public class BrAPIStudyCreation extends BrAPICreation<BrAPIStudy> {

    ExpUnitMiddlewareContext context;
    public BrAPIStudyCreation(ExpUnitMiddlewareContext context) {

        this.context = context;
    }

    /**
     * Abstract method to get the ExperimentImportEntity based on the ExpUnitMiddlewareContext.
     *
     * @return the ExperimentImportEntity object
     */
    @Override
    public ExperimentImportEntity<BrAPIStudy> getEntity() {
        try (ApplicationContext appContext = ApplicationContext.run()) {
            StudyService studyService = appContext.getBean(StudyService.class);
            BrAPIStudyDAO brAPIStudyDAO = appContext.getBean(BrAPIStudyDAO.class);
            ExperimentUtilities experimentUtilities = appContext.getBean(ExperimentUtilities.class);

            return new PendingStudy(context, studyService, brAPIStudyDAO, experimentUtilities);
        }
    }
}
