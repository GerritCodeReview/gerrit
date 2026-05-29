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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.common.ListTagSortOption;
import com.google.gerrit.extensions.common.WebLinkInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class TagSorterTest {
  private static final String revision = "dfdd715e31db256dfba48239f83f9b8da4bc243f";
  private static final boolean canDelete = true;
  private static final List<WebLinkInfo> webLinks = new ArrayList<>();
  private static final TagSorter tagSorter = new TagSorter();
  private List<TagInfo> tags;

  @Before
  public void initializeTags() {
    tags = createTags();
  }

  @Test
  public void testSortTagsByRef() {
    tagSorter.sort(ListTagSortOption.REF, tags, false);

    assertThat(tags.get(0).ref).isEqualTo("refs/tags/v1.0");
    assertThat(tags.get(1).ref).isEqualTo("refs/tags/v2.0");
    assertThat(tags.get(2).ref).isEqualTo("refs/tags/v3.0");
    assertThat(tags.get(3).ref).isEqualTo("refs/tags/v4.0");
  }

  @Test
  public void testSortTagsByCreationTime() {
    tagSorter.sort(ListTagSortOption.CREATION_TIME, tags, false);

    assertThat(tags.get(0).ref).isEqualTo("refs/tags/v2.0");
    assertThat(tags.get(1).ref).isEqualTo("refs/tags/v3.0");
    assertThat(tags.get(2).ref).isEqualTo("refs/tags/v1.0");
    assertThat(tags.get(3).ref).isEqualTo("refs/tags/v4.0");
  }

  @Test
  public void testSortTagsByCreationTimeDescendingOrder() {
    tagSorter.sort(ListTagSortOption.CREATION_TIME, tags, true);

    assertThat(tags.get(0).ref).isEqualTo("refs/tags/v4.0");
    assertThat(tags.get(1).ref).isEqualTo("refs/tags/v2.0");
    assertThat(tags.get(2).ref).isEqualTo("refs/tags/v3.0");
    assertThat(tags.get(3).ref).isEqualTo("refs/tags/v1.0");
  }

  private List<TagInfo> createTags() {
    Instant t1 = Instant.now();
    Instant t2 = t1.minusSeconds(10);
    Instant t3 = t1.minusSeconds(1);

    List<TagInfo> tags = new ArrayList<>();
    tags.add(new TagInfo("refs/tags/v1.0", revision, canDelete, webLinks, t1));
    tags.add(new TagInfo("refs/tags/v2.0", revision, canDelete, webLinks, t2));
    tags.add(new TagInfo("refs/tags/v3.0", revision, canDelete, webLinks, t3));
    tags.add(new TagInfo("refs/tags/v4.0", revision, canDelete, webLinks, (Instant) null));

    return tags;
  }
}
