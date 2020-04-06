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

package com.google.gerrit.server.git;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.RefNames;
import java.util.Collection;

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
    return new AutoValue_BranchOrderSection(ImmutableList.copyOf(order));
  }

  /**
   * Returns the branch that is more stable - so lower in the list ordered by priority. Always
   * returns a fully qualified ref name (including the refs/heads/ prefix).
   */
  public ImmutableList<String> getMoreStable(String branch) {
    ImmutableList<String> fullyQualifiedOrder =
        order().stream().map(RefNames::fullName).collect(toImmutableList());
    int i = fullyQualifiedOrder.indexOf(RefNames.fullName(branch));
    if (0 <= i) {
      return fullyQualifiedOrder.subList(i + 1, fullyQualifiedOrder.size());
    }
    return ImmutableList.of();
  }
}
