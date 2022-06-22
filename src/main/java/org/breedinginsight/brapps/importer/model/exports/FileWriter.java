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

package org.breedinginsight.brapps.importer.model.exports;

import io.micronaut.http.MediaType;
import io.micronaut.http.server.types.files.StreamedFile;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public abstract class FileWriter {

    //Takes in ByteArrayOutput stream and writes to file
    public static StreamedFile writeToDownload(ByteArrayOutputStream out, FileType fileType) throws IOException {
        try (out) {
            InputStream inputStream = new ByteArrayInputStream(out.toByteArray());
            MediaType fileVal = new MediaType(fileType.getMimeType(), fileType.getExtension());
            StreamedFile downloadFile = new StreamedFile(inputStream, fileVal);
            return downloadFile;
        } catch (IOException e) {
            log.info(e.getMessage());
            throw e;
        }
    }

    //Writes to output stream
    abstract ByteArrayOutputStream writeToOutputStream();

    //Calls writeToDownload, passes values into writeToOutputStream, returns output of writeToOutputStream
    abstract StreamedFile writeFile();
}
