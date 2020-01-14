package org.breedinginsight.api.bi.v1.controller.routes;

import io.micronaut.asm.commons.Method;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.web.router.DefaultRouteBuilder;
import org.breedinginsight.api.bi.model.v1.response.UserInfoResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.inject.ExecutableMethod;
import org.breedinginsight.api.bi.v1.controller.UserInfoController;
import org.jooq.tools.reflect.Reflect;

import java.lang.reflect.Executable;
import java.util.function.Function;

import javax.inject.Inject;
import java.security.Principal;


public class BiRoutes extends DefaultRouteBuilder {

    public BiRoutes(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
        super(executionHandleLocator, uriNamingStrategy);
    }

    @Inject
    void userRoutes(UserInfoController userController){
        // Gets the user information for a logged in user
        GET("/userinfo", userController, UserInfoController.USER_INFO_FUNCTION, Principal.class);
    }

}
