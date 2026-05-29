// Copyright (C) 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.restapi.project;

import static java.util.Comparator.comparing;

import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.common.ListTagSortOption;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;

public class TagSorter {
  @Inject
  public TagSorter() {}

  /** Sort the tags by the given sort option, in place */
  public void sort(ListTagSortOption sortBy, List<TagInfo> tags, boolean descendingOrder) {
    switch (sortBy) {
      case CREATION_TIME:
        Comparator<Timestamp> nullsComparator =
            descendingOrder
                ? Comparator.nullsFirst(Comparator.naturalOrder())
                : Comparator.nullsLast(Comparator.naturalOrder());
        tags.sort(comparing(t -> t.created, nullsComparator));
        break;
      case REF:
      default:
        tags.sort(comparing(t -> t.ref));
        break;
    }
  }
}
