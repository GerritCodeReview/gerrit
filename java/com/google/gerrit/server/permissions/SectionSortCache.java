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
// limitations under the License.

package com.google.gerrit.server.permissions;

import com.google.auto.value.AutoValue;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.util.MostSpecificComparator;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Caches the order AccessSections should be sorted for evaluation.
 *
 * <p>Access specifications for a more specific ref (eg. refs/heads/master rather than refs/heads/*)
 * take precedence in ACL evaluations. So for each combination of (ref, list of access specs) we
 * have to order the access specs by their distance from the ref to be matched. This is expensive,
 * so cache the sorted ordering.
 */
@Singleton
public class SectionSortCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CACHE_NAME = "permission_sort";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, EntryKey.class, EntryVal.class);
        bind(SectionSortCache.class);
      }
    };
  }

  private final Cache<EntryKey, EntryVal> cache;

  @Inject
  SectionSortCache(@Named(CACHE_NAME) Cache<EntryKey, EntryVal> cache) {
    this.cache = cache;
  }

  // Sorts the given sections, but does not disturb ordering between equally exact sections.
  void sort(String ref, List<AccessSection> sections) {
    final int cnt = sections.size();
    if (cnt <= 1) {
      return;
    }

    EntryKey key = EntryKey.create(ref, sections);
    EntryVal val = cache.getIfPresent(key);
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
      boolean poison = false;
      IdentityHashMap<AccessSection, Integer> srcMap = new IdentityHashMap<>();
      for (int i = 0; i < cnt; i++) {
        poison |= srcMap.put(sections.get(i), i) != null;
      }

      sections.sort(new MostSpecificComparator(ref));

      int[] srcIdx;
      if (isIdentityTransform(sections, srcMap)) {
        srcIdx = null;
      } else {
        srcIdx = new int[cnt];
        for (int i = 0; i < cnt; i++) {
          srcIdx[i] = srcMap.get(sections.get(i));
        }
      }

      if (poison) {
        logger.atSevere().log("Received duplicate AccessSection instances, not caching sort");
      } else {
        cache.put(key, new EntryVal(srcIdx));
      }
    }
  }

  private static AccessSection[] copy(List<AccessSection> sections) {
    return sections.toArray(new AccessSection[sections.size()]);
  }

  private static boolean isIdentityTransform(
      List<AccessSection> sections, IdentityHashMap<AccessSection, Integer> srcMap) {
    for (int i = 0; i < sections.size(); i++) {
      if (i != srcMap.get(sections.get(i))) {
        return false;
      }
    }
    return true;
  }

  @AutoValue
  abstract static class EntryKey {
    public abstract String ref();

    public abstract List<String> patterns();

    public abstract int cachedHashCode();

    static EntryKey create(String refName, List<AccessSection> sections) {
      int hc = refName.hashCode();
      List<String> patterns = new ArrayList<>(sections.size());
      for (AccessSection s : sections) {
        String n = s.getName();
        patterns.add(n);
        hc = hc * 31 + n.hashCode();
      }
      return new AutoValue_SectionSortCache_EntryKey(refName, ImmutableList.copyOf(patterns), hc);
    }

    @Override
    public final int hashCode() {
      return cachedHashCode();
    }
  }

  static final class EntryVal {
    /**
     * Maps the input index to the output index.
     *
     * <p>For {@code x == order[y]} the expression means move the item at source position {@code x}
     * to the output position {@code y}.
     */
    final int[] order;

    EntryVal(int[] order) {
      this.order = order;
    }
  }
}
