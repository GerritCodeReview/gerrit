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
  protected InheritedRefRight(){
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

  public boolean isInherited() {
    return inherited;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof InheritedRefRight) {
      RefRight myRight = getRight();
      RefRight otherRight = ((InheritedRefRight)o).getRight();
      if (otherRight.getAccountGroupId().equals(myRight.getAccountGroupId()) &&
          otherRight.getApprovalCategoryId().equals(myRight.getApprovalCategoryId()) &&
          otherRight.getMinValue() == myRight.getMinValue() &&
          otherRight.getMaxValue() == myRight.getMaxValue() &&
          otherRight.getRefPattern().equals(myRight.getRefPattern())) {
        return true;
      }
    }

    return false;
  }

  @Override
  public int hashCode() {
    RefRight myRight = getRight();
    int hashCode = myRight.getAccountGroupId().hashCode();
    hashCode += myRight.getApprovalCategoryId().hashCode();
    hashCode += myRight.getMinValue();
    hashCode += myRight.getMaxValue();
    hashCode += myRight.getRefPattern().hashCode();

    return hashCode;
  }
}
