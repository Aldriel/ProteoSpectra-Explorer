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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.client.RemoteServiceException;
import com.bioinfo.proteomics.client.ServiceUnavailableException;
import com.bioinfo.proteomics.desktop.i18n.Messages;
import com.bioinfo.proteomics.parser.ParsingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.stream.Stream;

class StatusMessageTest {

    private static final Messages EN = Messages.forLocale(Locale.ENGLISH);
    private static final Messages FR = Messages.forLocale(Locale.FRENCH);
    private static final URI REMOTE_URI = URI.create("https://example.org/x");

    static Stream<Arguments> exceptions() {
        return Stream.of(
                Arguments.of(
                        new RemoteServiceException(REMOTE_URI, 404, "gone"),
                        "error.remote.notFound"),
                Arguments.of(
                        new RemoteServiceException(REMOTE_URI, 500, null), "error.remote.status"),
                Arguments.of(new IOException("connection reset"), "error.network"),
                Arguments.of(new ServiceUnavailableException("down"), "error.unavailable"),
                Arguments.of(new ParsingException("/0", "bad json"), "error.parsing"),
                Arguments.of(new IllegalArgumentException("bad input"), "error.invalidInput"),
                Arguments.of(new IllegalStateException("bad state"), "error.invalidState"),
                Arguments.of(new UnsupportedOperationException(), "error.unexpected"));
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void exceptionsMapToTranslatedKeys(Exception e, String key) {
        StatusMessage status = StatusMessage.fromException(e);
        assertEquals(key, status.key());
        assertTrue(status.error());
        assertTrue(EN.has(key) && FR.has(key), key);
    }

    @Test
    void exceptionWithoutMessageUsesItsClassName() {
        assertEquals(
                "UnsupportedOperationException",
                StatusMessage.fromException(new UnsupportedOperationException()).detail());
    }

    @Test
    void renderInfoAndErrors() {
        StatusMessage info = StatusMessage.info("status.projectLoaded", "PXD000001", 9);
        assertEquals("Project PXD000001 loaded: 9 files.", info.render(EN));
        assertEquals("Projet PXD000001 chargé : 9 fichiers.", info.render(FR));
        assertNull(info.detail());
        assertFalse(info.error());

        assertEquals(
                "Error: the tolerance must be a positive number.",
                StatusMessage.error("error.tolerance").render(EN));
        assertEquals(
                "Erreur : accession introuvable (HTTP 404 from https://example.org/x: gone)",
                StatusMessage.fromException(new RemoteServiceException(REMOTE_URI, 404, "gone"))
                        .render(FR));
    }

    @Test
    void unavailableServicesAskToTryAgainLater() {
        StatusMessage status =
                StatusMessage.fromException(
                        new ServiceUnavailableException("PRIDE and ProteomeCentral are down"));
        assertEquals(
                "Error: PRIDE and ProteomeCentral are not responding, try again later",
                status.render(EN));
        assertEquals(
                "Erreur : PRIDE et ProteomeCentral ne répondent pas, réessayez plus tard",
                status.render(FR));
    }

    @Test
    void choiceAndPercentFormatsFollowTheLanguage() {
        assertEquals(
                "1 spectrum loaded.", StatusMessage.info("status.spectraLoaded", 1, 0).render(EN));
        assertEquals(
                "3 spectres chargés, 1 problème de lecture (voir en bas de l’onglet).",
                StatusMessage.info("status.spectraLoadedWithIssues", 3, 1).render(FR));
        assertTrue(StatusMessage.info("status.annotated", 23, 0.88).render(EN).contains("88%"));
    }

    @Test
    void argumentsAreDefensivelyCopied() {
        Object[] args = {"a"};
        StatusMessage status = new StatusMessage("status.reading", args, false, null);
        args[0] = "b";
        assertEquals("a", status.args()[0]);
        status.args()[0] = "c";
        assertEquals("a", status.args()[0]);
        assertThrows(NullPointerException.class, () -> StatusMessage.info(null));
    }
}
