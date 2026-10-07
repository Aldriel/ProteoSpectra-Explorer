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

package com.bioinfo.proteomics.desktop.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.mass.Tolerance;
import com.bioinfo.proteomics.model.MassType;
import com.bioinfo.proteomics.parser.ParseIssue;
import com.bioinfo.proteomics.parser.ParseMode;

import javafx.beans.binding.StringBinding;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

class MessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");
    private static final Pattern KEY = Pattern.compile("^[a-z]+(\\.[A-Za-z_]+)+$");
    private static final List<String> FXML_VIEWS =
            List.of(
                    "main-view.fxml",
                    "peptide-tab.fxml",
                    "spectra-tab.fxml",
                    "uniprot-tab.fxml",
                    "pride-tab.fxml");

    private final Messages en = Messages.forLocale(Locale.ENGLISH);
    private final Messages fr = Messages.forLocale(Locale.FRENCH);

    @Test
    void bothLanguagesDefineTheSameKeys() {
        assertEquals(new TreeSet<>(en.keys()), new TreeSet<>(fr.keys()));
    }

    @Test
    void translationsUseTheSamePlaceholders() {
        for (String key : en.keys()) {
            assertEquals(placeholders(en.pattern(key)), placeholders(fr.pattern(key)), key);
        }
    }

    @Test
    void noStraightApostrophes() {
        for (Messages m : List.of(en, fr)) {
            for (String key : m.keys()) {
                assertFalse(m.pattern(key).contains("'"), m.locale() + " " + key + " must use ’");
            }
        }
    }

    @Test
    void everyKeyFormats() {
        for (Messages m : List.of(en, fr)) {
            for (String key : m.keys()) {
                Object[] args = Stream.generate(() -> (Object) 1).limit(10).toArray();
                assertFalse(m.get(key, args).contains("!"), key);
            }
        }
    }

    @Test
    void everyEnumConstantShownInTheViewIsTranslated() {
        List<String> keys = new ArrayList<>();
        Stream.of(MassType.values()).forEach(v -> keys.add("massType." + v.name()));
        Stream.of(ParseMode.values()).forEach(v -> keys.add("parseMode." + v.name()));
        Stream.of(Tolerance.Unit.values()).forEach(v -> keys.add("toleranceUnit." + v.name()));
        Stream.of(Protease.values()).forEach(v -> keys.add("protease." + v.name()));
        Stream.of(ParseIssue.Severity.values()).forEach(v -> keys.add("issue." + v.name()));
        for (String key : keys) {
            assertTrue(en.has(key) && fr.has(key), key);
        }
    }

    @Test
    void everyKeyOfTheFxmlViewsExists() throws Exception {
        Set<String> used = new TreeSet<>();
        for (String view : FXML_VIEWS) {
            collectKeys("/com/bioinfo/proteomics/desktop/view/" + view, used);
        }
        assertTrue(used.size() > 50, "keys found: " + used.size());
        for (String key : used) {
            assertTrue(en.has(key), "missing key " + key);
        }
    }

    @Test
    void unknownKeysAndLanguages() {
        assertEquals("!nope!", en.get("nope"));
        assertFalse(en.has(null));
        assertEquals(Locale.ENGLISH, Messages.forLocale(Locale.JAPANESE).locale());
        assertEquals(Locale.ENGLISH, Messages.supportedLocale(null));
        assertEquals(Locale.FRENCH, Messages.supportedLocale(Locale.CANADA_FRENCH));
    }

    @Test
    void i18nBindingsFollowTheLanguage() {
        I18n i18n = new I18n(Locale.ENGLISH);
        StringBinding menu = i18n.text("menu.file");
        StringBinding version = i18n.text("about.version", "1.2");
        assertEquals("File", menu.get());
        i18n.setLocale(Locale.FRENCH);
        assertEquals("Fichier", menu.get());
        assertEquals("Version 1.2", version.get());
        assertEquals(Locale.FRENCH, i18n.locale());
        Messages before = i18n.messages();
        i18n.setLocale(Locale.CANADA_FRENCH);
        assertTrue(before == i18n.messages(), "same language: no change");
        i18n.setLocale(Locale.GERMAN);
        assertEquals("File", menu.get());
    }

    private static Set<String> placeholders(String pattern) {
        Set<String> found = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(pattern);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    private static void collectKeys(String resource, Set<String> keys) throws Exception {
        try (InputStream in = MessagesTest.class.getResourceAsStream(resource)) {
            assertTrue(in != null, "missing view " + resource);
            NodeList all =
                    DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder()
                            .parse(in)
                            .getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                NamedNodeMap attributes = ((Element) all.item(i)).getAttributes();
                for (String name : List.of("text", "promptText", "label")) {
                    if (attributes.getNamedItem(name) != null) {
                        String value = attributes.getNamedItem(name).getNodeValue();
                        if (KEY.matcher(value).matches()) {
                            keys.add(value);
                        }
                    }
                }
            }
        }
    }
}
