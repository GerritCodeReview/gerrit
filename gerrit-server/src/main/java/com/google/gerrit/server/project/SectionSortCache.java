// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.project;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.util.MostSpecificComparator;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/** Caches the order AccessSections should be sorted for evaluation. */
@Singleton
public class SectionSortCache {
  private static final String CACHE_NAME = "permission_sort";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<EntryKey, EntryVal>> type =
            new TypeLiteral<Cache<EntryKey, EntryVal>>() {};
        core(type, CACHE_NAME);
        bind(SectionSortCache.class);
      }
    };
  }

  private final Cache<EntryKey, EntryVal> cache;

  @Inject
  SectionSortCache(@Named(CACHE_NAME) Cache<EntryKey, EntryVal> cache) {
    this.cache = cache;
  }

  void sort(String ref, List<AccessSection> sections) {
    final int cnt = sections.size();
    if (cnt <= 1) {
      return;
    }

    EntryKey key = new EntryKey(ref, sections);
    EntryVal val = cache.get(key);
    if (val != null) {
      int[] srcIdx = val.order;
      if (srcIdx != null) {
        AccessSection[] srcList = copy(sections);
        for (int i = 0; i < cnt; i++) {
          sections.set(i, srcList[srcIdx[i]]);
        }
      } else {
        // Identity transform. No sorting is required.
      }

    } else {
      IdentityHashMap<AccessSection, Integer> srcMap =
          new IdentityHashMap<AccessSection, Integer>();
      for (int i = 0; i < cnt; i++) {
        srcMap.put(sections.get(i), i);
      }

      Collections.sort(sections, new MostSpecificComparator(ref));

      int srcIdx[];
      if (isIdentityTransform(sections, srcMap)) {
        srcIdx = null;
      } else {
        srcIdx = new int[cnt];
        for (int i = 0; i < cnt; i++) {
          srcIdx[i] = srcMap.get(sections.get(i));
        }
      }

      cache.put(key, new EntryVal(srcIdx));
    }
  }

  private static AccessSection[] copy(List<AccessSection> sections) {
    return sections.toArray(new AccessSection[sections.size()]);
  }

  private static boolean isIdentityTransform(List<AccessSection> sections,
      IdentityHashMap<AccessSection, Integer> srcMap) {
    for (int i = 0; i < sections.size(); i++) {
      if (i != srcMap.get(sections.get(i))) {
        return false;
      }
    }
    return true;
  }

  static final class EntryKey {
    private final String ref;
    private final String[] patterns;
    private final int hashCode;

    EntryKey(String refName, List<AccessSection> sections) {
      int hc = refName.hashCode();
      ref = refName;
      patterns = new String[sections.size()];
      for (int i = 0; i < patterns.length; i++) {
        String n = sections.get(i).getName();
        patterns[i] = n;
        hc = hc * 31 + n.hashCode();
      }
      hashCode = hc;
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof EntryKey) {
        EntryKey b = (EntryKey) other;
        return ref.equals(b.ref) && Arrays.equals(patterns, b.patterns);
      }
      return false;
    }
  }

  static final class EntryVal {
    /**
     * Maps the input index to the output index.
     * <p>
     * For {@code x == order[y]} the expression means move the item at
     * source position {@code x} to the output position {@code y}.
     */
    final int[] order;

    EntryVal(int[] order) {
      this.order = order;
    }
  }


}
