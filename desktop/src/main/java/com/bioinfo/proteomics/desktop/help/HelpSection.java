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

package com.bioinfo.proteomics.desktop.help;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Section of the user guide, introduced by a level-1 heading.
 *
 * @param id stable identifier, used to open the guide on a given section
 * @param title title shown in the list of sections
 * @param blocks content of the section
 */
public record HelpSection(String id, String title, List<Block> blocks) {

    public HelpSection {
        blocks = List.copyOf(blocks);
    }

    /** Returns the title and text of the section without markup. */
    public String plainText() {
        return title
                + "\n"
                + blocks.stream().map(Block::plainText).collect(Collectors.joining("\n"));
    }

    @Override
    public String toString() {
        return title;
    }
}
