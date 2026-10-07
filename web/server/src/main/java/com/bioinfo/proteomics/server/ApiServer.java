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

import com.bioinfo.proteomics.server.controller.PeptideController;
import com.bioinfo.proteomics.server.controller.PrideController;
import com.bioinfo.proteomics.server.controller.PrideDownloadController;
import com.bioinfo.proteomics.server.controller.SpectrumController;
import com.bioinfo.proteomics.server.controller.UniProtController;
import com.bioinfo.proteomics.service.ProteomicsService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * REST API consumed by the React front-end. Uses the JDK HTTP server and virtual threads.
 *
 * <p>Each route is served by a controller of the {@code controller} package, which delegates the
 * work to the shared {@link ProteomicsService} model.
 */
public final class ApiServer {

    /** Port used when neither an argument nor the {@code PORT} variable gives one. */
    public static final int DEFAULT_PORT = 7070;

    private static final Logger LOG = Logger.getLogger(ApiServer.class.getName());

    private ApiServer() {}

    /**
     * Creates a server with every route registered; the caller starts it.
     *
     * @param host address to listen on
     * @param port port to listen on, or 0 for any free port
     * @param service model the controllers delegate to
     * @return the server, not started
     * @throws IOException if the address cannot be bound
     */
    public static HttpServer create(String host, int port, ProteomicsService service)
            throws IOException {
        return create(host, port, service, new PrideDownloads(service));
    }

    /**
     * Creates a server whose PRIDE downloads are run by the given model; the caller starts it.
     *
     * @param host address to listen on
     * @param port port to listen on, or 0 for any free port
     * @param service model the controllers delegate to
     * @param downloads model running the downloads of PRIDE files
     * @return the server, not started
     * @throws IOException if the address cannot be bound
     */
    public static HttpServer create(
            String host, int port, ProteomicsService service, PrideDownloads downloads)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/api/health", new HealthController());
        server.createContext("/api/peptide", new PeptideController(service));
        server.createContext("/api/spectra", new SpectrumController(service));
        server.createContext("/api/uniprot", new UniProtController(service));
        server.createContext("/api/pride", new PrideController(service));
        server.createContext("/api/pride/downloads", new PrideDownloadController(downloads));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        return server;
    }

    /**
     * Starts the API on {@code 127.0.0.1}.
     *
     * @param args optional port number; defaults to the {@code PORT} variable, then 7070
     * @throws IOException if the port is already in use
     */
    public static void main(String[] args) throws IOException {
        String port = args.length > 0 ? args[0] : System.getenv("PORT");
        int portNumber = port == null ? DEFAULT_PORT : Integer.parseInt(port);
        HttpServer server = create("127.0.0.1", portNumber, ProteomicsService.createDefault());
        server.start();
        int actualPort = server.getAddress().getPort();
        LOG.info(() -> "PRIDE API listening on http://127.0.0.1:" + actualPort + "/api");
    }

    /** {@code GET /api/health}: lets clients check that the server is running. */
    private static final class HealthController extends JsonHandler {

        HealthController() {
            super("/api/health");
        }

        @Override
        protected Object handle(Request request) {
            return Map.of("status", "UP");
        }
    }
}
