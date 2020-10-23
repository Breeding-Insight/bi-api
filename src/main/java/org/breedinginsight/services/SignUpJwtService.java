/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.services;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.server.exceptions.HttpServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.breedinginsight.api.model.v1.auth.SignUpJWT;
import org.breedinginsight.services.exceptions.JwtValidationException;

import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class SignUpJwtService {

    @Property(name = "micronaut.security.token.jwt.signatures.secret.generator.secret")
    private String jwtSecret;
    @Property(name = "micronaut.security.token.jwt.signatures.secret.generator.jws-algorithm")
    private JWSAlgorithm jwsAlgorithm;
    @Property(name = "web.signup.url-timeout")
    private Duration jwtDuration;

    // Returns
    public SignUpJWT validateAndParseAccountSignUpJwt(String accountToken) throws JwtValidationException {

        SignedJWT signedJWT;
        Map<String, Object> claims;
        SignUpJWT signUpJWT = new SignUpJWT();
        try {
            signedJWT = SignedJWT.parse(accountToken);
            JWTClaimsSet claimSet = signedJWT.getJWTClaimsSet();
            signUpJWT.setJwtId(UUID.fromString(claimSet.getJWTID()));
            claims = claimSet.getClaims();
        } catch (ParseException | IllegalArgumentException e) {
            throw new JwtValidationException(e.getMessage());
        }

        JWSVerifier verifier;
        Boolean verified;
        try {
            verifier = new MACVerifier(jwtSecret);
            verified = signedJWT.verify(verifier);
        } catch (JOSEException e) {
            throw new JwtValidationException(e.getMessage());
        }

        if (!verified) {
            throw new JwtValidationException("Invalid jwt");
        }

        // Get the user id and the jwt id
        // TODO: Check if jwtId could possibly be null here
        if (!claims.containsKey("uid")){
            throw new JwtValidationException("UUID missing");
        }

        try {
            String id = claims.get("uid").toString();
            signUpJWT.setUserId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException(e.getMessage());
        }

        return signUpJWT;
    }

    public SignUpJWT generateNewAccountJWT(UUID userId) {

        // Create new account token
        JWSSigner signer;
        try {
            signer = new MACSigner(jwtSecret);
        } catch (KeyLengthException e) {
            throw new HttpServerException(e.getMessage());
        }

        // Send user id as payload so we know who to check later
        Long expirationTime = new Date().getTime() + jwtDuration.toMillis();
        UUID jwtId = UUID.randomUUID();
        SignUpJWT signUpJWT = new SignUpJWT();
        signUpJWT.setJwtId(jwtId);
        signUpJWT.setUserId(userId);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .jwtID(jwtId.toString())
                .claim("uid", userId.toString())
                .expirationTime(new Date(expirationTime))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(jwsAlgorithm), claimsSet);

        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new HttpServerException(e.getMessage());
        }

        signUpJWT.setSignedJWT(jwt);

        return signUpJWT;
    }
}
