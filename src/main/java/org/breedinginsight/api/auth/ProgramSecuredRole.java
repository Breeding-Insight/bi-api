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

package org.breedinginsight.api.auth;

public enum ProgramSecuredRole {
    MEMBER("MEMBER"),
    BREEDER("BREEDER"),
    SYSTEM_ADMIN("ADMIN");

    private String domain;

    ProgramSecuredRole(String domain) {
        this.domain = domain;
    }

    @Override
    public String toString() {
        return domain;
    }

    public static ProgramSecuredRole getEnum(String domain) {
        for(ProgramSecuredRole v : values())
            if(v.toString().equalsIgnoreCase(domain)) return v;
        throw new IllegalArgumentException();
    }
}
