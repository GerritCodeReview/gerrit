// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.common.data;

import java.util.List;

/**
 * It holds list of branches and boolean to indicate
 * if it is allowed to add new branches.
 */
public final class ListBranchesResult {
  protected boolean canAdd;

  protected List<BranchInfo> branches;

  protected ListBranchesResult() {
  }

  public ListBranchesResult(final List<BranchInfo> branches, boolean canAdd) {
    this.branches = branches;
    this.canAdd = canAdd;
  }

  public boolean getCanAdd() {
    return canAdd;
  }

  public void setCanAdd(boolean canAdd) {
    this.canAdd = canAdd;
  }

  public List<BranchInfo> getBranches() {
    return branches;
  }

  public void setBranches(List<BranchInfo> branches) {
    this.branches = branches;
  }
}
