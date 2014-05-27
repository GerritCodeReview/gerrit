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

import org.eclipse.jgit.lib.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BranchOrderSection {

  /**
   * Branch names ordered from least to the most stable.
   *
   * Typically the order will be like: master, stable-M.N, stable-M.N-1, ...
   */
  private final List<String> order;

  public BranchOrderSection(String[] order) {
    this.order = new ArrayList<>(order.length);
    for (String b : order) {
      this.order.add(fullName(b));
    }
  }

  private static String fullName(String branch) {
    if (branch.startsWith(Constants.R_HEADS)) {
      return branch;
    } else {
      return Constants.R_HEADS + branch;
    }
  }

  public List<String> getMoreStable(String branch) {
    int i = order.indexOf(fullName(branch));
    if (0 <= i) {
      return order.subList(i + 1, order.size());
    } else {
      return Collections.emptyList();
    }
  }
}
