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

/** Non-successful HTTP response from a remote service. */
public class RemoteServiceException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final transient URI uri;

    /**
     * Creates the exception.
     *
     * @param uri address that was requested
     * @param statusCode HTTP status code of the response
     * @param message beginning of the response body, or {@code null}
     */
    public RemoteServiceException(URI uri, int statusCode, String message) {
        super(
                "HTTP "
                        + statusCode
                        + " from "
                        + uri
                        + (message == null || message.isBlank() ? "" : ": " + message));
        this.uri = uri;
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public URI uri() {
        return uri;
    }

    /**
     * Tells whether the requested resource does not exist (HTTP 404).
     *
     * @return {@code true} for a 404 response
     */
    public boolean isNotFound() {
        return statusCode == 404;
    }
}
