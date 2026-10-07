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

package com.bioinfo.proteomics.digest;

/**
 * A peptide produced by in-silico digestion.
 *
 * @param sequence plain sequence
 * @param start 1-based start position in the protein
 * @param end 1-based inclusive end position in the protein
 * @param missedCleavages number of internal cleavage sites not cleaved
 */
public record DigestedPeptide(String sequence, int start, int end, int missedCleavages) {}
