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

import com.google.gerrit.reviewdb.RefMergeStrategy;

/**
 * Additional data about a {@link RefMergeStrategy} not normally loaded: defines if a
 * ref merge strategy is inherited from a parent structure (e.g. a parent project).
 */
public class InheritedRefMergeStrategy {
  private RefMergeStrategy refMergeStrategy;
  private boolean inherited;

  /**
   * Creates a instance of a {@link RefMergeStrategy} with data about inheritance
   */
  protected InheritedRefMergeStrategy() {
  }

  /**
   * Creates a instance of a {@link RefMergeStrategy} with data about inheritance
   *
   * @param refMergeStrategy the ref merge strategy
   * @param inherited true if the ref merge strategy is inherited, false otherwise
   */
  public InheritedRefMergeStrategy(RefMergeStrategy refMergeStrategy, boolean inherited) {
    this.refMergeStrategy = refMergeStrategy;
    this.inherited = inherited;
  }

  public RefMergeStrategy getRefMergeStrategy() {
    return refMergeStrategy;
  }

  public boolean isInherited() {
    return inherited;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof InheritedRefMergeStrategy) {
      InheritedRefMergeStrategy a = this;
      InheritedRefMergeStrategy b = (InheritedRefMergeStrategy) o;
      return a.getRefMergeStrategy().equals(b.getRefMergeStrategy())
          && a.isInherited() == b.isInherited();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getRefMergeStrategy().hashCode();
  }
}
