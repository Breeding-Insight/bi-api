package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.model.Location;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class ProgramLocationService {



    public List<Location> getProgramLocations(UUID programId) throws DoesNotExistException {
        /* Get the locations associated with a program. */
        //TODO
        return new ArrayList<>();
    }

    public Location getProgramLocation(UUID programId, UUID locationId) throws DoesNotExistException {
        /* Get a specific location for a program. */
        //TODO
        return null;
    }

    public Location addProgramLocation(UUID programId, ProgramLocationRequest programLocationRequest) throws DoesNotExistException, AlreadyExistsException {
        /* Add a location to a program. */
        //TODO
        return null;
    }

    public void removeProgramLocation(UUID programId, UUID locationId) throws DoesNotExistException {
        /* Removes a location from a program. Does not delete the location object. */
        //TODO
    }
}
