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

package com.bioinfo.proteomics.server;

/** Error mapped to an HTTP status code by {@link JsonHandler}. */
public class HttpError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    /**
     * Creates an error.
     *
     * @param status HTTP status code sent to the client, such as 400 or 404
     * @param message explanation sent to the client
     */
    public HttpError(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status code sent to the client.
     *
     * @return the status code
     */
    public int status() {
        return status;
    }
}
