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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Locates the {@code samples} folder shipped with the application: next to the executable in the
 * packaged application, or in the project directory when run from Maven.
 */
public final class SampleFiles {

    /** System property overriding the location of the samples folder. */
    public static final String DIRECTORY_PROPERTY = "pride.samples.dir";

    /** Set by jpackage launchers to the path of the executable. */
    static final String APP_PATH_PROPERTY = "jpackage.app-path";

    private SampleFiles() {}

    /** Returns the samples folder, if one exists. */
    public static Optional<Path> directory() {
        return directory(
                System.getProperty(DIRECTORY_PROPERTY),
                System.getProperty(APP_PATH_PROPERTY),
                Path.of("").toAbsolutePath());
    }

    static Optional<Path> directory(String override, String appPath, Path workingDirectory) {
        List<Path> candidates = new ArrayList<>();
        if (override != null && !override.isBlank()) {
            candidates.add(Path.of(override));
        }
        if (appPath != null && !appPath.isBlank()) {
            Path parent = Path.of(appPath).toAbsolutePath().getParent();
            if (parent != null) {
                candidates.add(parent.resolve("samples"));
            }
        }
        candidates.add(workingDirectory.resolve("samples"));
        Path parent = workingDirectory.getParent();
        if (parent != null) {
            candidates.add(parent.resolve("samples"));
        }
        return candidates.stream().filter(Files::isDirectory).map(Path::normalize).findFirst();
    }
}
