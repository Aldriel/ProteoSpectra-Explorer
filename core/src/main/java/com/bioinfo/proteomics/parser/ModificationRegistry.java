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

package com.bioinfo.proteomics.parser;

import com.bioinfo.proteomics.model.Modification;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves modifications by name (case-insensitive) or Unimod accession ({@code UNIMOD:35}). */
public final class ModificationRegistry {

    private final Map<String, Modification> byName = new ConcurrentHashMap<>();
    private final Map<Integer, Modification> byUnimodId = new ConcurrentHashMap<>();

    /** Registry pre-loaded with common proteomics modifications. */
    public static ModificationRegistry withDefaults() {
        ModificationRegistry registry = new ModificationRegistry();
        registry.register(Modification.CARBAMIDOMETHYL);
        registry.register(Modification.OXIDATION);
        registry.register(Modification.PHOSPHO);
        registry.register(Modification.ACETYL);
        registry.register(Modification.DEAMIDATED);
        registry.register(Modification.METHYL);
        registry.register(Modification.AMIDATED);
        return registry;
    }

    /**
     * Adds a modification, replacing any modification of the same name or Unimod id.
     *
     * @param modification modification to add
     * @return this registry, so that calls can be chained
     */
    public ModificationRegistry register(Modification modification) {
        Objects.requireNonNull(modification, "modification");
        byName.put(modification.name().toLowerCase(Locale.ROOT), modification);
        if (modification.unimodId() > 0) {
            byUnimodId.put(modification.unimodId(), modification);
        }
        return this;
    }

    /**
     * Looks up a modification.
     *
     * @param key name in any case ({@code oxidation}) or Unimod accession ({@code UNIMOD:35})
     * @return the modification, or empty if it is unknown
     */
    public Optional<Modification> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String trimmed = key.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("unimod:")) {
            try {
                return Optional.ofNullable(byUnimodId.get(Integer.parseInt(lower.substring(7))));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(byName.get(lower));
    }
}
