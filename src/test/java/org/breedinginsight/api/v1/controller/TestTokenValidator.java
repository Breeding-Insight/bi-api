package org.breedinginsight.api.v1.controller;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.DefaultAuthentication;
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration;
import io.micronaut.security.token.jwt.signature.SignatureConfiguration;
import io.micronaut.security.token.jwt.validator.GenericJwtClaimsValidator;
import io.micronaut.security.token.jwt.validator.JwtAuthenticationFactory;
import io.micronaut.security.token.jwt.validator.JwtTokenValidator;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;

@Replaces(JwtTokenValidator.class)
@Singleton
public class TestTokenValidator extends JwtTokenValidator {

    public static final String TEST_USER_ORCID = "1111-2222-3333-4444";

    public TestTokenValidator(Collection<SignatureConfiguration> signatureConfigurations, Collection<EncryptionConfiguration> encryptionConfigurations, Collection<GenericJwtClaimsValidator> genericJwtClaimsValidators, JwtAuthenticationFactory jwtAuthenticationFactory) {
        super(signatureConfigurations, encryptionConfigurations, genericJwtClaimsValidators, jwtAuthenticationFactory);
    }

    public Publisher<Authentication> validateToken(String token) {
        if (token.equals("test-registered-user")) {
            return Flowable.just(new DefaultAuthentication("1111-2222-3333-4444", new HashMap<>()));
        }
        else {
            return Flowable.just(new DefaultAuthentication("1111-1111-1111-1111", new HashMap<>()));
        }

    }

}
