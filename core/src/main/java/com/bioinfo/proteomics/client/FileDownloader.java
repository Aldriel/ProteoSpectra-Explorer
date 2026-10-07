/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
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

package com.bioinfo.proteomics.client;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.function.LongConsumer;

/** Downloads files to the disk, so that services can be tested without network access. */
@FunctionalInterface
public interface FileDownloader {

    /**
     * Downloads a file. The download stops with an {@link java.io.InterruptedIOException} as soon
     * as the calling thread is interrupted; the partial file is then deleted.
     *
     * @param uri address of the file
     * @param target file to create or replace
     * @param progress receives the number of bytes received so far, from time to time
     * @throws RemoteServiceException for non-2xx responses
     * @throws IOException for transport or disk errors
     */
    void download(URI uri, Path target, LongConsumer progress) throws IOException;

    /**
     * Asks the server for the size of a file without downloading it.
     *
     * @param uri address of the file
     * @return the size in bytes, or empty if the server does not tell it
     * @throws IOException for transport errors
     */
    default OptionalLong size(URI uri) throws IOException {
        return OptionalLong.empty();
    }
}
