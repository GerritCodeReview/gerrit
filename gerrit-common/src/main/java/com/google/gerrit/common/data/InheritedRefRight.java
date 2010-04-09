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

import com.google.gerrit.reviewdb.RefRight;

/**
 * Additional data about a {@link RefRight} not normally loaded: defines if a right is
 * inherited from a parent structure (e.g. a parent project).
 */
public class InheritedRefRight  {

  private RefRight right;
  private boolean inherited;

  /**
   * Creates a instance of a {@link RefRight} with data about inheritance
   */
  public InheritedRefRight(){
  }

  /**
   * Creates a instance of a {@link RefRight} with data about inheritance
   * @param right the right
   * @param inherited true if the right is inherited, false otherwise
   */
  public InheritedRefRight(RefRight right, boolean inherited){
    this.right = right;
    this.inherited = inherited;
  }

  public RefRight getRight() {
    return right;
  }

  public void setRight(RefRight right) {
    this.right = right;
  }

  public void setInherited(boolean inherited) {
    this.inherited = inherited;
  }

  public boolean isInherited() {
    return inherited;
  }

  @Override
  public boolean equals(Object o) {
    InheritedRefRight inheritedRefRight = (InheritedRefRight) o;
    RefRight refRight = inheritedRefRight.getRight();

    if (refRight.getAccountGroupId().equals(this.getRight().getAccountGroupId()) &&
        refRight.getApprovalCategoryId().equals(this.getRight().getApprovalCategoryId()) &&
        refRight.getMinValue() == this.getRight().getMinValue() &&
        refRight.getMaxValue() == this.getRight().getMaxValue() &&
        refRight.getRefPattern().equals(this.getRight().getRefPattern())) {
      return true;
    }

    return false;
  }
}
