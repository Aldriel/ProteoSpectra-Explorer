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

package com.bioinfo.proteomics.desktop.model;

import com.bioinfo.proteomics.client.RemoteServiceException;
import com.bioinfo.proteomics.client.ServiceUnavailableException;
import com.bioinfo.proteomics.desktop.i18n.Messages;
import com.bioinfo.proteomics.parser.ParsingException;

import java.io.IOException;
import java.util.Objects;

/**
 * Message of the status bar, kept as a key and arguments so it is translated again when the
 * language changes.
 *
 * @param key message key
 * @param args message arguments
 * @param error whether the message reports a failure
 * @param detail technical detail appended to errors (messages of the core library, in English); may
 *     be {@code null}
 */
public record StatusMessage(String key, Object[] args, boolean error, String detail) {

    public static final StatusMessage READY = info("status.ready");

    public StatusMessage {
        Objects.requireNonNull(key, "key");
        args = args.clone();
    }

    public static StatusMessage info(String key, Object... args) {
        return new StatusMessage(key, args, false, null);
    }

    /** Returns an error message without technical detail. */
    public static StatusMessage error(String key, Object... args) {
        return new StatusMessage(key, args, true, null);
    }

    /**
     * Returns an error message describing {@code e}, with a translated explanation and the raw
     * detail.
     */
    public static StatusMessage fromException(Exception e) {
        if (e instanceof ServiceUnavailableException) {
            return error("error.unavailable");
        }
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String key =
                switch (e) {
                    case RemoteServiceException remote when remote.isNotFound() ->
                            "error.remote.notFound";
                    case RemoteServiceException remote -> "error.remote.status";
                    case IOException io -> "error.network";
                    case ParsingException parsing -> "error.parsing";
                    case IllegalArgumentException invalid -> "error.invalidInput";
                    case IllegalStateException state -> "error.invalidState";
                    default -> "error.unexpected";
                };
        return new StatusMessage(key, new Object[0], true, detail);
    }

    @Override
    public Object[] args() {
        return args.clone();
    }

    /** Returns the message in the language of {@code m}. */
    public String render(Messages m) {
        String text = m.get(key, args);
        if (!error) {
            return text;
        }
        String message = detail == null ? text : m.get("status.errorDetail", text, detail);
        return m.get("status.error", message);
    }
}
