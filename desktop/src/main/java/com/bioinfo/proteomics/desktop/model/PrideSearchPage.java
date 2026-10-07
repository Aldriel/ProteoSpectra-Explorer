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

import com.bioinfo.proteomics.model.PrideProject;

import java.util.List;
import java.util.Objects;

/**
 * One page of PRIDE search results.
 *
 * @param keywords words searched
 * @param page index of the page, starting at 0
 * @param projects projects of the page
 * @param lastPage whether no page follows this one
 */
public record PrideSearchPage(
        String keywords, int page, List<PrideProject> projects, boolean lastPage) {

    /** Requires the keywords and copies the projects. */
    public PrideSearchPage {
        Objects.requireNonNull(keywords, "keywords");
        projects = List.copyOf(projects);
    }

    /** Returns the rank of the first project of the page, starting at 1. */
    public int firstRank() {
        return page * DesktopModel.PRIDE_PAGE_SIZE + 1;
    }

    /** Returns the rank of the last project of the page. */
    public int lastRank() {
        return page * DesktopModel.PRIDE_PAGE_SIZE + projects.size();
    }
}
