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

/*
 From https://breedinginsight.atlassian.net/jira/software/c/projects/BI/boards/1?selectedIssue=BI-2203

 ...constraint should be added to the bi_user table that prevents the orcid id from being non-null if the email is null.
 The reverse (non-null email and null orcid) must still be allowed since it is the state for a user pending verification.
 */

ALTER TABLE bi_user DROP CONSTRAINT IF EXISTS email_orcid;

ALTER TABLE bi_user
ADD CONSTRAINT email_orcid CHECK ( (email IS NOT NULL ) OR (orcid IS NULL) ) ;