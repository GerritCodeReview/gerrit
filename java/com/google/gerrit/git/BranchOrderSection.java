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

package com.google.gerrit.git;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.RefNames;
import java.util.List;

public class BranchOrderSection {

  /**
   * Branch names ordered from least to the most stable.
   *
   * <p>Typically the order will be like: master, stable-M.N, stable-M.N-1, ...
   */
  private final ImmutableList<String> order;

  public BranchOrderSection(String[] order) {
    if (order.length == 0) {
      this.order = ImmutableList.of();
    } else {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      for (String b : order) {
        builder.add(RefNames.fullName(b));
      }
      this.order = builder.build();
    }
  }

  public String[] getMoreStable(String branch) {
    int i = order.indexOf(RefNames.fullName(branch));
    if (0 <= i) {
      List<String> r = order.subList(i + 1, order.size());
      return r.toArray(new String[r.size()]);
    }
    return new String[] {};
  }
}
