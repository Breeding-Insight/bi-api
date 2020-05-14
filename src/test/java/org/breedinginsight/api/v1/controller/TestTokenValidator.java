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
import org.breedinginsight.model.User;
import org.breedinginsight.services.UserService;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

@Replaces(JwtTokenValidator.class)
@Singleton
public class TestTokenValidator extends JwtTokenValidator {

    @Inject
    UserService userService;

    public static final String TEST_USER_ORCID = "1111-2222-3333-4444";
    public static final String OTHER_TEST_USER_ORCID = "5555-6666-7777-8888";
    public static final String NON_EXISTENT_USER_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    public TestTokenValidator(Collection<SignatureConfiguration> signatureConfigurations, Collection<EncryptionConfiguration> encryptionConfigurations, Collection<GenericJwtClaimsValidator> genericJwtClaimsValidators, JwtAuthenticationFactory jwtAuthenticationFactory) {
        super(signatureConfigurations, encryptionConfigurations, genericJwtClaimsValidators, jwtAuthenticationFactory);
    }

    public Publisher<Authentication> validateToken(String token) {
        if (token.equals("test-registered-user")) {
            Optional<User> testUser = userService.getByOrcid(TEST_USER_ORCID);
            Map<String, Object> adminClaims = new HashMap<>();
            List<String> roles = new ArrayList<>();
            roles.add("ADMIN");
            adminClaims.put("roles", roles);
            adminClaims.put("id", testUser.get().getId());
            return Flowable.just(new DefaultAuthentication(TEST_USER_ORCID, adminClaims));
        } else if (token.equals("other-registered-user")) {
            Optional<User> otherTestUser = userService.getByOrcid(OTHER_TEST_USER_ORCID);
            Map<String, Object> userClaims = new HashMap<>();
            List<String> roles = new ArrayList<>();
            userClaims.put("roles", roles);
            userClaims.put("id", otherTestUser.get().getId());
            return Flowable.just(new DefaultAuthentication(OTHER_TEST_USER_ORCID, userClaims));
        } else if (token.equals("non-existent-user")){
            Map<String, Object> adminClaims = new HashMap<>();
            List<String> roles = new ArrayList<>();
            roles.add("ADMIN");
            adminClaims.put("roles", roles);
            adminClaims.put("id", NON_EXISTENT_USER_ID);
            return Flowable.just(new DefaultAuthentication(NON_EXISTENT_USER_ID, adminClaims));
        } else {
            return Flowable.just(new DefaultAuthentication("1111-1111-1111-1111", new HashMap<>()));
        }
    }

}
