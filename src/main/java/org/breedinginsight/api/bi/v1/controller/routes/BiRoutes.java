package org.breedinginsight.api.bi.v1.controller.routes;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.http.HttpMethod;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.UriRoute;
import org.breedinginsight.api.bi.model.v1.request.UserRequest;
import org.breedinginsight.api.bi.v1.controller.UserController;

import javax.inject.Inject;
import java.security.Principal;
import java.util.UUID;

public class BiRoutes extends DefaultRouteBuilder {

    public BiRoutes(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
        super(executionHandleLocator, uriNamingStrategy);
    }

    public UriRoute GET(String uri, Object target, String method, Class... parameterTypes) {
        return this.buildRoute(HttpMethod.GET, uriNamingStrategy.resolveUri(uri), target.getClass(), method, parameterTypes);
    }

    public UriRoute POST(String uri, Object target, String method, Class... parameterTypes) {
        return this.buildRoute(HttpMethod.POST, uriNamingStrategy.resolveUri(uri), target.getClass(), method, parameterTypes);
    }

    public UriRoute PUT(String uri, Object target, String method, Class... parameterTypes) {
        return this.buildRoute(HttpMethod.PUT, uriNamingStrategy.resolveUri(uri), target.getClass(), method, parameterTypes);
    }

    public UriRoute DELETE(String uri, Object target, String method, Class... parameterTypes) {
        return this.buildRoute(HttpMethod.DELETE, uriNamingStrategy.resolveUri(uri), target.getClass(), method, parameterTypes);
    }

    @Inject
    void userRoutes(UserController userController) {

        // Gets the user information for a logged in user
        GET("/userinfo", userController, UserController.USER_INFO_FUNCTION, Principal.class);

        // Gets the list of users
        GET("/users", userController, UserController.USERS_FUNCTION, Principal.class);

        // Get a single user
        GET("/users/{userId}", userController, UserController.USERS_FUNCTION, Principal.class, UUID.class);

        // Creates a new user
        POST("/users", userController, UserController.CREATE_USER_FUNCTION, Principal.class, UserRequest.class);

        // Updates a user
        PUT("/users/{userId}", userController, UserController.UPDATE_USER_FUNCTION, Principal.class, UUID.class, UserRequest.class);

        // Deletes a user
        DELETE("/users/{userId}", userController, UserController.DELETE_USER_FUNCTION, Principal.class, UUID.class);
    }


}
