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

import com.google.gerrit.reviewdb.NewRefRight;

/**
 * Additional data about a {@link NewRefRight} not normally loaded: defines if a
 * right is inherited from a parent structure (e.g. a parent project).
 */
public class InheritedNewRefRight {
  private NewRefRight right;
  private boolean inherited;
  private boolean owner;

  /**
   * Creates a instance of a {@link NewRefRight} with data about inheritance
   */
  protected InheritedNewRefRight() {
  }

  /**
   * Creates a instance of a {@link NewRefRight} with data about inheritance
   *
   * @param right the right
   * @param inherited true if the right is inherited, false otherwise
   * @param owner true if right is owned by current user, false otherwise
   */
  public InheritedNewRefRight(NewRefRight right, boolean inherited, boolean owner) {
    this.right = right;
    this.inherited = inherited;
    this.owner = owner;
  }

  public NewRefRight getRight() {
    return right;
  }

  public boolean isInherited() {
    return inherited;
  }

  public boolean isOwner() {
    return owner;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof InheritedNewRefRight) {
      InheritedNewRefRight a = this;
      InheritedNewRefRight b = (InheritedNewRefRight) o;
      return a.getRight().equals(b.getRight())
          && a.isInherited() == b.isInherited();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return getRight().hashCode();
  }
}
