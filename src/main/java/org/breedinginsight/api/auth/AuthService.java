package org.breedinginsight.api.auth;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.token.jwt.cookie.JwtCookieConfiguration;
import io.micronaut.security.token.jwt.cookie.JwtCookieLoginHandler;
import io.micronaut.security.token.jwt.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.jwt.generator.JwtGeneratorConfiguration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.breedinginsight.dao.db.Tables.BI_USER;

@Replaces(JwtCookieLoginHandler.class)
@Singleton
public class AuthService extends JwtCookieLoginHandler {

    @Inject
    DSLContext dsl;

    public AuthService(JwtCookieConfiguration jwtCookieConfiguration,
                       JwtGeneratorConfiguration jwtGeneratorConfiguration,
                       AccessRefreshTokenGenerator accessRefreshTokenGenerator) {
        super(jwtCookieConfiguration, jwtGeneratorConfiguration, accessRefreshTokenGenerator);
    }

    @Override
    public HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
        System.out.println("loginSuccess: User = " + userDetails.getUsername());

        // User has been authenticated against orcid, check they have a bi account.
        Result<Record> result = dsl.select().from(BI_USER).where(BI_USER.ORCID.eq(userDetails.getUsername())).fetch();

        // For now, if we have found a record, let them through
        HttpResponse response;
        if (result.size() > 0){
            response = super.loginSuccess(userDetails, request);
        }
        else {
            // If they are not in our database, fail our authentication
            AuthenticationFailed authFailed = new AuthenticationFailed();
            response = super.loginFailed(authFailed);
        }


        return response;
    }

    @Override
    public HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
        System.out.println("loginFailed");
        return super.loginFailed(authenticationFailed);
    }
}