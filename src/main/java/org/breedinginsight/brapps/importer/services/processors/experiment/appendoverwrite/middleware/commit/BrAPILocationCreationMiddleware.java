package org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.commit;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.brapps.importer.services.processors.experiment.ExperimentUtilities;
import org.breedinginsight.brapps.importer.services.processors.experiment.appendoverwrite.middleware.ExpUnitMiddleware;
import org.breedinginsight.brapps.importer.services.processors.experiment.model.ExpUnitMiddlewareContext;
import org.breedinginsight.model.ProgramLocation;
import org.breedinginsight.services.ProgramLocationService;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.MissingRequiredInfoException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Prototype
public class BrAPILocationCreationMiddleware extends ExpUnitMiddleware {

    ExperimentUtilities experimentUtilities;
    ProgramLocationService programLocationService;
    private List<ProgramLocationRequest> newLocations;
    @Inject
    public BrAPILocationCreationMiddleware(ExperimentUtilities experimentUtilities, ProgramLocationService programLocationService) {
        this.experimentUtilities = experimentUtilities;
        this.programLocationService = programLocationService;
    }
    @Override
    public boolean process(ExpUnitMiddlewareContext context) {
        // Construct request
        newLocations = experimentUtilities.getNewObjects(context.getPendingData().getLocationByName(), ProgramLocation.class)
                .stream()
                .map(location -> ProgramLocationRequest.builder()
                        .name(location.getName())
                        .build())
                .collect(Collectors.toList());

        // Create acting user
        AuthenticatedUser actingUser = new AuthenticatedUser(context.getImportContext().getUpload().getUpdatedByUser().getName(), new ArrayList<>(), context.getImportContext().getUpload().getUpdatedByUser().getId(), new ArrayList<>());

        // Create new locations in brapi service
        List<ProgramLocation> createdLocations = null;
        try {
            createdLocations = new ArrayList<>(programLocationService.create(actingUser, context.getImportContext().getProgram().getId(), newLocations));

            // Update the context cache
            for (ProgramLocation createdLocation : createdLocations) {

                // Set the system-generated dbId for each newly created location
                context.getPendingData().getLocationByName().get(createdLocation.getName()).getBrAPIObject().setLocationDbId(createdLocation.getLocationDbId());

                // Set the location dbid for cached studies
                context.getPendingData().getStudyByNameNoScope().values().stream()
                        .filter(study -> createdLocation.getId().toString().equals(study.getBrAPIObject().getLocationDbId()))
                        .forEach(study -> study.getBrAPIObject().setLocationDbId(createdLocation.getLocationDbId()));
            }

        } catch (MissingRequiredInfoException e) {
            throw new RuntimeException(e);
        } catch (UnprocessableEntityException e) {
            throw new RuntimeException(e);
        } catch (DoesNotExistException e) {
            throw new RuntimeException(e);
        }

        return processNext(context);
    }
}
