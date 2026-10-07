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

package com.bioinfo.proteomics.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Name, version and author of the application. */
public final class AppInfo {

    /** Name of the application, shown in the title bar and the About dialog. */
    public static final String NAME = "ProteoSpectra-Explorer";

    /** Author of the application. */
    public static final String AUTHOR = "Maxime Ethier";

    /** Contact address shown in the About dialog. */
    public static final String CONTACT_EMAIL = "contact@maximeethier.com";

    /** Page where users can support the author with a donation. */
    public static final String KOFI_URL = "https://ko-fi.com/K1S228CL7A";

    /** Text of the licence under which the application is distributed. */
    public static final String LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0";

    private static final String VERSION = loadVersion();

    private AppInfo() {}

    /** Returns the version of the application, set by the Maven build. */
    public static String version() {
        return VERSION;
    }

    /**
     * Returns the version written into {@code app.properties} by the build, or {@code "dev"}
     * outside a Maven build.
     */
    private static String loadVersion() {
        try (InputStream in = AppInfo.class.getResourceAsStream("app.properties")) {
            if (in == null) {
                return "dev";
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version", "dev");
            return version.startsWith("${") ? "dev" : version;
        } catch (IOException e) {
            return "dev";
        }
    }
}
