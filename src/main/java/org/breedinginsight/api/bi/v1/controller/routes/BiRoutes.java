package org.breedinginsight.api.bi.v1.controller.routes;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.web.router.DefaultRouteBuilder;
import org.breedinginsight.api.bi.model.v1.request.UserRequest;
import org.breedinginsight.api.bi.v1.controller.UserController;

import javax.inject.Inject;
import java.security.Principal;

public class BiRoutes extends DefaultRouteBuilder {

    public BiRoutes(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
        super(executionHandleLocator, uriNamingStrategy);
    }

    @Inject
    void userRoutes(UserController userController){
        // Gets the user information for a logged in user
        GET("/userinfo", userController, UserController.USER_INFO_FUNCTION, Principal.class);

        // Gets the list of users
        GET("/users", userController, UserController.USERS_FUNCTION, Principal.class);

        // Get a single user
        GET("/users/{userId}", userController, UserController.USERS_FUNCTION, Principal.class, Integer.class);

        // Creates a new user
        POST("/users", userController, UserController.CREATE_USER_FUNCTION, Principal.class, UserRequest.class);

        // Updates a user
        PUT("/users", userController, UserController.UPDATE_USER_FUNCTION, Principal.class, UserRequest.class);

        // Deletes a user
        DELETE("/users/{userId}", userController, UserController.DELETE_USER_FUNCTION, Principal.class, Integer.class);
    }


}
