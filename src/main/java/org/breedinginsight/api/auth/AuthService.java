package org.breedinginsight.api.auth;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.event.LoginFailedEvent;
import io.micronaut.security.event.LoginSuccessfulEvent;
import io.micronaut.security.handlers.LoginHandler;

import javax.inject.Singleton;

/*
public class AuthService implements LoginHandler {

    @Override
    public HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
        return null;
    }

    @Override
    public HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
        return null;
    }
}
*/

@Singleton
class LoginSuccessfulEventListener implements ApplicationEventListener<LoginSuccessfulEvent> {
    @Override
    public void onApplicationEvent(LoginSuccessfulEvent event) {
        UserDetails user = (UserDetails)event.getSource();
        System.out.println("LoginSuccessfulEvent: User = " + user.getUsername());
    }
}

@Singleton
class LoginFailedEventListener implements ApplicationEventListener<LoginFailedEvent> {
    @Override
    public void onApplicationEvent(LoginFailedEvent event) {
        AuthenticationFailed failed = (AuthenticationFailed)event.getSource();
        System.out.println("LoginFailedEvent: " + failed.getMessage());
    }
}
