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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

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

  /**
   * Sorts the given sections in-place, but does not disturb ordering between equally exact
   * sections.
   */
  void sort(String ref, List<AccessSection> sections) {
    final int cnt = sections.size();
    if (cnt <= 1) {
      return;
    }
    try {
      EntryKey key = EntryKey.create(ref, sections);
      EntryVal val = cache.get(key, new Loader(key, sections));
      ImmutableList<Integer> order = val.order();
      List<AccessSection> sorted = new ArrayList<>();
      for (int i = 0; i < cnt; i++) {
        sorted.add(sections.get(order.get(i)));
      }
      for (int i = 0; i < cnt; i++) {
        sections.set(i, sorted.get(i));
      }
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Error happened while sorting access sections.");
    }
  }

  private static class Loader implements Callable<EntryVal> {
    List<AccessSection> sections;
    EntryKey key;

    Loader(EntryKey key, List<AccessSection> sections) {
      this.key = key;
      this.sections = sections;
    }

    @Override
    public EntryVal call() throws Exception {
      IdentityHashMap<AccessSection, Integer> srcMap = new IdentityHashMap<>();
      for (int i = 0; i < sections.size(); i++) {
        srcMap.put(sections.get(i), i);
      }
      ImmutableList<AccessSection> sorted =
          sections.stream()
              .sorted(new MostSpecificComparator(key.ref()))
              .collect(ImmutableList.toImmutableList());
      ImmutableList.Builder<Integer> order = ImmutableList.builderWithExpectedSize(sections.size());
      for (int i = 0; i < sorted.size(); i++) {
        order.add(srcMap.get(sorted.get(i)));
      }
      return EntryVal.create(order.build());
    }
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

  @AutoValue
  abstract static class EntryVal {
    /**
     * Maps the input index to the output index.
     *
     * <p>For {@code x == order[y]} the expression means move the item at source position {@code x}
     * to the output position {@code y}.
     */
    abstract ImmutableList<Integer> order();

    static EntryVal create(ImmutableList<Integer> order) {
      return new AutoValue_SectionSortCache_EntryVal(order);
    }
  }
}
