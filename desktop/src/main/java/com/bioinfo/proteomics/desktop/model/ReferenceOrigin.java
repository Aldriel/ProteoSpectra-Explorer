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

import java.util.Objects;

/**
 * Where the reference spectra compared with the selected spectrum come from.
 *
 * @param kind second file, spectra already loaded, or theoretical spectrum
 * @param name name of the file for {@link Kind#FILE}, the peptide for {@link Kind#THEORETICAL};
 *     empty for {@link Kind#LOADED}
 */
public record ReferenceOrigin(Kind kind, String name) {

    /** Source of the reference spectra. */
    public enum Kind {
        /** Spectra read from a second file. */
        FILE,
        /** One of the spectra of the "Spectra" tab. */
        LOADED,
        /** Spectrum computed from a peptide. */
        THEORETICAL
    }

    /** Validates the origin. */
    public ReferenceOrigin {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
    }
}
