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

package org.breedinginsight.db.migration;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.daos.UserDAO;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Slf4j
public class V1_32_0__Set_Dev_Admin_Email extends BaseJavaMigration {

    @Inject
    private DSLContext dsl;
    @Inject
    private UserDAO userDAO;

    final private String ORCID_SANDBOX_AUTHENTICATION = "orcid-sandbox-authentication";

    public void migrate(Context context) throws Exception {
        Map<String, String> placeholders = context.getConfiguration().getPlaceholders();
        boolean isOrcidSandboxAuthentication =  Boolean.parseBoolean( placeholders.get(ORCID_SANDBOX_AUTHENTICATION) );
        updateDevAdminUser(context, isOrcidSandboxAuthentication);
        //Must delete user "Chris Tucker" before adding the new Constraint (User "Chris Tucker" is added by migration V0.5.2__populate-user-data.sql.
        // It would violate the new constraint)
        // PS -- We miss Chris Tucker.
        deleteUserChris(context);
        addConstraint(context);
    }

    private void updateDevAdminUser(Context context, boolean isOrcidSandboxAuthentication) throws SQLException {
        String biDevAdminUserEmail = isOrcidSandboxAuthentication ?
                "bidevteam@mailinator.com" : "bidevteam@cornell.edu";
        try (Statement update = context.getConnection().createStatement()) {
            String sql = "UPDATE bi_user SET email='" + biDevAdminUserEmail + "' WHERE NULLIF(email,'') IS NULL AND bi_user.name='BI-DEV Admin'";
            log.debug(sql);
            update.executeUpdate(sql);
        }
    }

    private void deleteUserChris(Context context) throws SQLException {
        try (Statement delete = context.getConnection().createStatement()) {
            String sql = "DELETE FROM bi_user WHERE name = 'Chris Tucker'  AND email IS NULL";
            log.debug(sql);
            delete.executeUpdate(sql);
        }
    }

    private void addConstraint(Context context) throws SQLException {
        final String CONSTRAINT_NAME = "email_orcid";
        // First, drop the constraint if it already exist.
        try (Statement altTable = context.getConnection().createStatement()) {
            String sql = "ALTER TABLE bi_user DROP CONSTRAINT IF EXISTS "+ CONSTRAINT_NAME;
            log.debug(sql);
            altTable.executeUpdate(sql);
        }

        // Add new constraint
        try (Statement altTable = context.getConnection().createStatement()) {
            String sql = "ALTER TABLE bi_user\n" +
                    "ADD CONSTRAINT " +CONSTRAINT_NAME+ " CHECK ( (email IS NOT NULL ) OR (orcid IS NULL) ) ;";
            log.debug(sql);
            altTable.executeUpdate(sql);
        }
    }
}