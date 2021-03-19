// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.server.permissions.SectionSortCache.EntryKey;
import com.google.gerrit.server.permissions.SectionSortCache.EntryVal;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Test for {@link SectionSortCache} */
public class SectionSortCacheTest {
  private SectionSortCache sectionSortCache;
  private Cache<EntryKey, EntryVal> cache;

  private static final AccessSection sectionA = AccessSection.create("refs/heads/branch_1");
  private static final AccessSection sectionB = AccessSection.create("refs/base/branch_2");
  private static final String REF_BASE = "refs/base";

  @Before
  public void setup() {
    cache = CacheBuilder.newBuilder().build();
    sectionSortCache = new SectionSortCache(cache);
  }

  @Test
  public void sortSingleElement() {
    List<AccessSection> input = new ArrayList<>();
    input.add(sectionA);
    sectionSortCache.sort(REF_BASE, input);
    assertThat(input).containsExactly(sectionA);
  }

  @Test
  public void sortMultiElements() {
    List<AccessSection> input = new ArrayList<>();
    input.add(sectionA);
    input.add(sectionB);
    sectionSortCache.sort(REF_BASE, input);
    assertThat(input).containsExactly(sectionB, sectionA).inOrder();
  }

  @Test
  public void sortMultiElementsIdentity() {
    List<AccessSection> input = new ArrayList<>();
    input.add(sectionB);
    input.add(sectionA);
    sectionSortCache.sort(REF_BASE, input);
    assertThat(input).containsExactly(sectionB, sectionA).inOrder();
  }

  @Test
  public void sortMultiElementsWithDuplicates() {
    List<AccessSection> input = new ArrayList<>();
    input.add(sectionB);
    input.add(sectionA);
    input.add(sectionA);
    input.add(sectionA);
    input.add(sectionB);
    sectionSortCache.sort(REF_BASE, input);
    assertThat(input).containsExactly(sectionB, sectionB, sectionA, sectionA, sectionA).inOrder();
  }
}
