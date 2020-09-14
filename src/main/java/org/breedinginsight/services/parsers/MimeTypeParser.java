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

package org.breedinginsight.services.parsers;

import io.micronaut.http.multipart.CompletedFileUpload;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class MimeTypeParser {

    private Detector detector;

    public MimeTypeParser() {
        TikaConfig config = TikaConfig.getDefaultConfig();
        this.detector = config.getDetector();
    }

    public MediaType getMimeType(CompletedFileUpload file) throws IOException {
        org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
        metadata.add(Metadata.RESOURCE_NAME_KEY, file.getFilename());
        TikaInputStream tikaStream = TikaInputStream.get(file.getInputStream());
        return detector.detect(tikaStream, metadata);
    }


}
