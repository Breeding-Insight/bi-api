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

package org.breedinginsight.utilities.email;

import io.micronaut.context.annotation.Context;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STRawGroupDir;

import javax.inject.Singleton;
import java.io.File;

@Singleton
@Context
public class EmailTemplates {
    private String NEW_ACCOUNT_EMAIL = "newAccountEmail";
    private File templateDir = new File("src/main/resources/email/");
    private STRawGroupDir templates;

    public EmailTemplates() {
        templates = new STRawGroupDir(templateDir.getAbsolutePath());
        // Check that all of the emails exist
        if (templates.getInstanceOf(NEW_ACCOUNT_EMAIL) == null) {throw new IllegalStateException();}
    }

    public ST getNewSignupTemplate() {
        return templates.getInstanceOf(NEW_ACCOUNT_EMAIL);
    }
}
