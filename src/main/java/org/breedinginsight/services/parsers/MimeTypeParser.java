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
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;

import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Singleton
public class MimeTypeParser {

    private final TikaConfig config;
    private final Detector detector;

    public MimeTypeParser() {
        config = TikaConfig.getDefaultConfig();
        this.detector = config.getDetector();
    }

    public MediaType getMediaType(CompletedFileUpload file) throws IOException {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, file.getFilename());
        TikaInputStream tikaStream = TikaInputStream.get(file.getInputStream());
        return detector.detect(tikaStream, metadata);
    }

    public MimeType getMimeType(CompletedFileUpload file) throws IOException, MimeTypeException {
        MediaType mediaType = getMediaType(file);
        return config.getMimeRepository()
                     .getRegisteredMimeType(mediaType.toString());
    }

    public MediaType getMediaType(byte[] fileContents, String filename) throws IOException {
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        TikaInputStream tikaStream = TikaInputStream.get(new ByteArrayInputStream(fileContents));
        return detector.detect(tikaStream, metadata);
    }

    public MimeType getMimeType(byte[] fileContents, String filename) throws IOException, MimeTypeException {
        MediaType mediaType = getMediaType(fileContents, filename);
        return config.getMimeRepository()
                     .getRegisteredMimeType(mediaType.toString());
    }

}
