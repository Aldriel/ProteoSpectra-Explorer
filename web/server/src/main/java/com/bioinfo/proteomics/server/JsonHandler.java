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

import com.bioinfo.proteomics.client.RemoteServiceException;
import com.bioinfo.proteomics.client.ServiceUnavailableException;
import com.bioinfo.proteomics.parser.JsonSupport;
import com.bioinfo.proteomics.parser.ParsingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class of the REST controllers: JSON serialization, CORS, request size limits and translation
 * of exceptions into HTTP status codes.
 *
 * <p>Subclasses only implement {@link #handle(Request)}, which returns the object to send back.
 * Errors are answered as {@code {"error": "...", "location": "..."}}, with the status given by
 * {@link #describe(Exception)}.
 */
public abstract class JsonHandler implements HttpHandler {

    /** Largest JSON request body accepted, in bytes. */
    static final int MAX_BODY_BYTES = 10 * 1024 * 1024;

    /** Largest spectrum file accepted by {@link Request#upload()}, in bytes. */
    static final long MAX_UPLOAD_BYTES = 1024L * 1024 * 1024;

    private static final int MEGABYTE = 1024 * 1024;

    private static final Logger LOG = Logger.getLogger(JsonHandler.class.getName());

    /** Category of an error, which the web interface translates like the desktop application. */
    public enum Kind {
        /** Malformed input data (peptide, spectrum document). */
        PARSING,
        /** Invalid request or parameter. */
        INVALID_INPUT,
        /** Resource unknown to the remote service, or to this API. */
        NOT_FOUND,
        /** The remote service answered with an error. */
        REMOTE,
        /** The remote service cannot be reached. */
        NETWORK,
        /** Every remote service able to answer is down: the user should try again later. */
        UNAVAILABLE,
        /** The operation is not possible in the current state. */
        INVALID_STATE,
        /** The uploaded file or the request body is too large. */
        TOO_LARGE,
        /** Bug or unexpected failure. */
        UNEXPECTED
    }

    /**
     * Error sent to the client.
     *
     * @param status HTTP status code
     * @param kind category of the error
     * @param message explanation, in English
     * @param location where the error is in the input, or {@code null}
     */
    public record Failure(int status, Kind kind, String message, String location) {}

    /**
     * File returned by a controller instead of a JSON document: it is sent as an attachment, so
     * that the browser saves it rather than displaying it.
     *
     * @param path file to send
     * @param fileName name proposed to the user
     */
    public record FileResponse(Path path, String fileName) {}

    /**
     * Decoded HTTP request handed to the controllers.
     *
     * @param method HTTP method, in upper case
     * @param subPath path after the context prefix, without leading slash (may be empty)
     * @param query decoded query parameters; the first value wins when a name is repeated
     * @param content raw request body, read by {@link #body()} or {@link #upload()}
     */
    public record Request(
            String method, String subPath, Map<String, String> query, InputStream content) {

        /**
         * Reads the request body as UTF-8 text, such as a JSON document.
         *
         * @return the body
         * @throws HttpError with status 413 if the body exceeds {@value #MAX_BODY_BYTES} bytes
         * @throws IOException if the body cannot be read
         */
        public String body() throws IOException {
            byte[] bytes = content.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new HttpError(
                        413, "Request body exceeds " + MAX_BODY_BYTES / MEGABYTE + " MB");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        /**
         * Returns the request body as a stream, for large uploaded files.
         *
         * @return the body; reading beyond {@link #MAX_UPLOAD_BYTES} throws a {@link
         *     BodyTooLargeException}
         */
        public InputStream upload() {
            return new LimitedInputStream(content, MAX_UPLOAD_BYTES);
        }

        /**
         * Returns a query parameter, stripped of surrounding spaces.
         *
         * @param name parameter name
         * @param defaultValue value returned when the parameter is absent or blank
         * @return the value, or {@code defaultValue}
         */
        public String param(String name, String defaultValue) {
            String v = query.get(name);
            return v == null || v.isBlank() ? defaultValue : v.strip();
        }

        /**
         * Returns a mandatory query parameter.
         *
         * @param name parameter name
         * @return the value, stripped of surrounding spaces
         * @throws HttpError with status 400 if the parameter is absent or blank
         */
        public String requiredParam(String name) {
            String v = param(name, null);
            if (v == null) {
                throw new HttpError(400, "Missing query parameter '" + name + "'");
            }
            return v;
        }

        /**
         * Returns an integer query parameter.
         *
         * @param name parameter name
         * @param defaultValue value returned when the parameter is absent or blank
         * @return the value, or {@code defaultValue}
         * @throws HttpError with status 400 if the value is not an integer
         */
        public int intParam(String name, int defaultValue) {
            return (int) longParam(name, defaultValue, Integer.MAX_VALUE);
        }

        /**
         * Returns a long integer query parameter, such as a size in bytes.
         *
         * @param name parameter name
         * @param defaultValue value returned when the parameter is absent or blank
         * @return the value, or {@code defaultValue}
         * @throws HttpError with status 400 if the value is not an integer
         */
        public long longParam(String name, long defaultValue) {
            return longParam(name, defaultValue, Long.MAX_VALUE);
        }

        private long longParam(String name, long defaultValue, long max) {
            String v = param(name, null);
            if (v == null) {
                return defaultValue;
            }
            try {
                long value = Long.parseLong(v);
                if (Math.abs(value) > max) {
                    throw new NumberFormatException(v);
                }
                return value;
            } catch (NumberFormatException e) {
                throw new HttpError(400, "Query parameter '" + name + "' must be an integer");
            }
        }

        /**
         * Returns an enum query parameter, ignoring case.
         *
         * @param <E> enum type
         * @param name parameter name
         * @param type enum class
         * @param defaultValue value returned when the parameter is absent or blank
         * @return the constant, or {@code defaultValue}
         * @throws HttpError with status 400 if no constant has this name
         */
        public <E extends Enum<E>> E enumParam(String name, Class<E> type, E defaultValue) {
            String v = param(name, null);
            if (v == null) {
                return defaultValue;
            }
            try {
                return Enum.valueOf(type, v.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new HttpError(400, "Invalid value '" + v + "' for '" + name + "'");
            }
        }

        /**
         * Decodes the JSON body into a record.
         *
         * @param <T> type of the record
         * @param type class of the record
         * @param what name of the request, used in the error message
         * @return the decoded body; never {@code null}
         * @throws HttpError with status 400 if the body is not valid JSON for {@code type}
         * @throws IOException if the body cannot be read
         */
        public <T> T json(Class<T> type, String what) throws IOException {
            T value;
            try {
                value = JsonSupport.mapper().readValue(body(), type);
            } catch (JsonProcessingException e) {
                throw new HttpError(400, "Invalid " + what + " request: " + e.getOriginalMessage());
            }
            if (value == null) {
                throw new HttpError(400, "Empty " + what + " request");
            }
            return value;
        }
    }

    /** Thrown when an uploaded file exceeds {@link #MAX_UPLOAD_BYTES}. */
    public static final class BodyTooLargeException extends IOException {

        private static final long serialVersionUID = 1L;

        BodyTooLargeException(long limit) {
            super("Uploaded file exceeds " + limit / MEGABYTE + " MB");
        }
    }

    private final String contextPath;

    /**
     * Creates a controller.
     *
     * @param contextPath path prefix the controller is registered under, such as {@code /api/pride}
     */
    protected JsonHandler(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * Processes a request.
     *
     * @param request decoded request
     * @return the object to serialize as JSON, sent with status 200, or a {@link FileResponse}
     * @throws IOException if a remote service cannot be reached
     */
    protected abstract Object handle(Request request) throws IOException;

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            int status = 200;
            Object payload;
            try {
                payload = handle(toRequest(exchange));
            } catch (IOException | RuntimeException e) {
                Failure failure = describe(e);
                if (failure.status() == 500) {
                    LOG.log(Level.SEVERE, "Unexpected error on " + exchange.getRequestURI(), e);
                }
                status = failure.status();
                payload = error(failure);
            }
            if (payload instanceof FileResponse file) {
                sendFile(exchange, file);
            } else {
                sendJson(exchange, status, payload);
            }
        }
    }

    /**
     * Translates an exception into the error sent to the client:
     *
     * <ul>
     *   <li>the status of an {@link HttpError};
     *   <li>400 for invalid input ({@link ParsingException}, {@link IllegalArgumentException});
     *   <li>404 when the remote service does not know the requested resource;
     *   <li>413 when an uploaded file is too large;
     *   <li>503 when every remote service able to answer is down;
     *   <li>502 when the remote service is unreachable or fails;
     *   <li>500 for any other error.
     * </ul>
     *
     * @param e exception thrown while processing a request
     * @return the status code and message
     */
    public static Failure describe(Exception e) {
        return switch (e) {
            case HttpError http ->
                    new Failure(http.status(), kindOf(http.status()), http.getMessage(), null);
            case ParsingException parsing ->
                    new Failure(400, Kind.PARSING, parsing.detail(), parsing.location());
            case IllegalArgumentException invalid ->
                    new Failure(400, Kind.INVALID_INPUT, invalid.getMessage(), null);
            case RemoteServiceException remote when remote.isNotFound() ->
                    new Failure(404, Kind.NOT_FOUND, "Resource not found on remote service", null);
            case RemoteServiceException remote ->
                    new Failure(502, Kind.REMOTE, remote.getMessage(), null);
            case ServiceUnavailableException unavailable ->
                    new Failure(503, Kind.UNAVAILABLE, unavailable.getMessage(), null);
            case BodyTooLargeException tooLarge ->
                    new Failure(413, Kind.TOO_LARGE, tooLarge.getMessage(), null);
            case IOException io ->
                    new Failure(
                            502,
                            Kind.NETWORK,
                            "Remote service unavailable: " + io.getMessage(),
                            null);
            default -> new Failure(500, Kind.UNEXPECTED, "Internal server error", null);
        };
    }

    private static Kind kindOf(int status) {
        return switch (status) {
            case 400, 405 -> Kind.INVALID_INPUT;
            case 404 -> Kind.NOT_FOUND;
            case 409, 429 -> Kind.INVALID_STATE;
            case 413 -> Kind.TOO_LARGE;
            default -> Kind.UNEXPECTED;
        };
    }

    /**
     * Allows the Vite development server, which runs on another port, to call the API.
     *
     * @param exchange current exchange
     */
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders()
                .add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int status, Object payload)
            throws IOException {
        byte[] bytes = JsonSupport.mapper().writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendFile(HttpExchange exchange, FileResponse file) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders()
                .set("Content-Disposition", contentDisposition(file.fileName()));
        exchange.sendResponseHeaders(200, Files.size(file.path()));
        try (OutputStream out = exchange.getResponseBody()) {
            Files.copy(file.path(), out);
        }
    }

    /**
     * Returns the Content-Disposition header of an attachment: an ASCII name for old clients, and
     * the exact name encoded in UTF-8 (RFC 6266).
     *
     * @param fileName name proposed to the user
     * @return the header value
     */
    static String contentDisposition(String fileName) {
        String ascii = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private Request toRequest(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String sub =
                path.length() > contextPath.length() ? path.substring(contextPath.length()) : "";
        while (sub.startsWith("/")) {
            sub = sub.substring(1);
        }
        return new Request(
                exchange.getRequestMethod().toUpperCase(Locale.ROOT),
                sub,
                parseQuery(exchange.getRequestURI().getRawQuery()),
                exchange.getRequestBody());
    }

    /**
     * Decodes a URL query string. Parameters without value map to an empty string; when a name is
     * repeated, the first value is kept.
     *
     * @param rawQuery encoded query, without the leading {@code ?}; may be {@code null}
     * @return the parameters, in order of appearance
     */
    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private static String decode(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    /**
     * Rejects a request whose HTTP method is not the expected one.
     *
     * @param request current request
     * @param method expected method, in upper case
     * @throws HttpError with status 405 if the methods differ
     */
    protected static void requireMethod(Request request, String method) {
        if (!request.method().equals(method)) {
            throw new HttpError(405, "Method " + request.method() + " not allowed, use " + method);
        }
    }

    private static Map<String, Object> error(Failure failure) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", failure.message() == null ? "Unknown error" : failure.message());
        body.put("kind", failure.kind());
        if (failure.location() != null && !failure.location().isEmpty()) {
            body.put("location", failure.location());
        }
        return body;
    }

    /** Stream failing with a {@link BodyTooLargeException} once more than a limit is read. */
    private static final class LimitedInputStream extends FilterInputStream {

        private final long limit;
        private long count;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        private void count(int n) throws BodyTooLargeException {
            count += n;
            if (count > limit) {
                throw new BodyTooLargeException(limit);
            }
        }
    }
}
