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

/** Minimal HTTP GET abstraction, so that remote clients can be tested without network access. */
@FunctionalInterface
public interface HttpFetcher {

    /**
     * Returns the response body of a successful (2xx) GET request.
     *
     * @param uri address to request
     * @return the response body, decoded as UTF-8
     * @throws RemoteServiceException for non-2xx responses
     * @throws IOException for transport errors
     */
    String get(URI uri) throws IOException;
}
