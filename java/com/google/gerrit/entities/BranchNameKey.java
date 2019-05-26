// Copyright (C) 2019 The Android Open Source Project
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

import com.google.auto.value.AutoValue;

/** Branch name key */
@AutoValue
public abstract class BranchNameKey implements Comparable<BranchNameKey> {
  public static BranchNameKey create(Project.NameKey projectName, String branchName) {
    return new AutoValue_BranchNameKey(projectName, RefNames.fullName(branchName));
  }

  public static BranchNameKey create(String projectName, String branchName) {
    return create(Project.nameKey(projectName), branchName);
  }

  public abstract Project.NameKey project();

  public abstract String branch();

  public String shortName() {
    return RefNames.shortName(branch());
  }

  @Override
  public final int compareTo(BranchNameKey o) {
    // TODO(dborowitz): Only compares branch name in order to match old StringKey behavior.
    // Consider comparing project name first.
    return branch().compareTo(o.branch());
  }

  @Override
  public final String toString() {
    return project() + "," + KeyUtil.encode(branch());
  }
}
