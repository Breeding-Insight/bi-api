package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.ProgramLocationRequest;
import org.breedinginsight.api.model.v1.request.ProgramRequest;
import org.breedinginsight.api.model.v1.request.ProgramUserRequest;
import org.breedinginsight.daos.ProgramDao;
import org.breedinginsight.model.Location;
import org.breedinginsight.model.Program;
import org.breedinginsight.model.User;
import org.breedinginsight.services.exceptions.AlreadyExistsException;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Singleton
public class ProgramService {

    @Inject
    private ProgramDao dao;

    public Program getById(UUID programId) throws DoesNotExistException {
        /* Get Program by program ID */
        return null;
    }

    public List<Program> getAll(){
        /* Get all of the programs */
        //TODO
        return new ArrayList<>();
    }

    public Program create(ProgramRequest programRequest) throws AlreadyExistsException {
        /* Create a program from a request object */
        // TODO
        return null;
    }

    public Program updateProgram(UUID programId, ProgramRequest programRequest) throws DoesNotExistException {
        /* Update an existing program */
        //TODO
        return null;
    }

    public void archiveProgram(UUID programId) throws DoesNotExistException {
        /* Archive an existing program */
        //TODO
    }

    public List<User> getProgramUsers(UUID programId) throws DoesNotExistException {
        /* Get all of the users in the program */
        //TODO
        return new ArrayList<>();
    }

    public User getProgramUserbyId(UUID programId, UUID userId) throws DoesNotExistException {
        /* Get a program user by their id */
        //TODO
        return null;
    }

    public User addProgramUser(UUID programId, ProgramUserRequest programUserRequest) throws DoesNotExistException, AlreadyExistsException {
        /* Add a user to a program. Create the user if they don't exist. */
        //TODO
        return null;
    }

    public void removeProgramUser(UUID programId, UUID userId) throws DoesNotExistException {
        /* Remove a user from a program, but don't delete the user. */
        //TODO
    }

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
