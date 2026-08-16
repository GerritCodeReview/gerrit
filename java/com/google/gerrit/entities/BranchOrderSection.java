// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.entities;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;

/**
 * An ordering of branches by stability.
 *
 * <p>The REST API supports automatically checking if changes on development branches can be merged
 * into stable branches. This is configured by the {@code branchOrder.branch} project setting. This
 * class represents the ordered list of branches, by increasing stability.
 */
@AutoValue
public abstract class BranchOrderSection {

  /**
   * Branch names ordered from least to the most stable.
   *
   * <p>Typically the order will be like: master, stable-M.N, stable-M.N-1, ...
   *
   * <p>Ref names in this list are exactly as they appear in {@code project.config}
   */
  public abstract ImmutableList<String> order();

  public static BranchOrderSection create(Collection<String> order) {
    // Do not mutate the given list as this will be written back to disk when ProjectConfig is
    // stored.
    return new AutoValue_BranchOrderSection(ImmutableList.copyOf(order));
  }

  @Memoized
  ImmutableList<String> fullyQualifiedOrder() {
    return order().stream().map(RefNames::fullName).collect(toImmutableList());
  }

  @Memoized
  ImmutableMap<String, ImmutableList<String>> moreStableMap() {
    ImmutableList<String> fqOrder = fullyQualifiedOrder();
    Map<String, ImmutableList<String>> map = Maps.newHashMapWithExpectedSize(fqOrder.size());
    for (int i = 0; i < fqOrder.size(); i++) {
      String branch = fqOrder.get(i);
      map.putIfAbsent(branch, fqOrder.subList(i + 1, fqOrder.size()));
    }
    return ImmutableMap.copyOf(map);
  }

  /**
   * Returns the tail list of branches that are more stable - so lower in the entire list ordered by
   * priority compared to the provided branch. Always returns a fully qualified ref name (including
   * the refs/heads/ prefix).
   */
  public ImmutableList<String> getMoreStable(String branch) {
    if (branch == null) {
      return ImmutableList.of();
    }
    ImmutableList<String> moreStable = moreStableMap().get(RefNames.fullName(branch));
    return moreStable != null ? moreStable : ImmutableList.of();
  }
}
