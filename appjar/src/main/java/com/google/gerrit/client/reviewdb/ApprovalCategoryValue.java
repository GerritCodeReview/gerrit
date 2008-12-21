// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;

/** Valid value for a {@link ApprovalCategory}. */
public final class ApprovalCategoryValue {
  public static class Key implements
      com.google.gwtorm.client.Key<ApprovalCategory.Id> {
    @Column
    protected ApprovalCategory.Id categoryId;

    @Column
    protected short value;

    protected Key() {
      categoryId = new ApprovalCategory.Id();
    }

    public Key(final ApprovalCategory.Id cat, final short v) {
      categoryId = cat;
      value = v;
    }

    public ApprovalCategory.Id getParentKey() {
      return categoryId;
    }

    @Override
    public int hashCode() {
      return categoryId.hashCode() * 31 + value;
    }

    @Override
    public boolean equals(final Object o) {
      return o instanceof Key && ((Key) o).categoryId.equals(categoryId)
          && ((Key) o).value == value;
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  @Column(length = 50)
  protected String name;

  protected ApprovalCategoryValue() {
  }

  public ApprovalCategoryValue(final ApprovalCategoryValue.Key id,
      final String name) {
    this.key = id;
    this.name = name;
  }

  public ApprovalCategory.Id getCategoryId() {
    return key.categoryId;
  }

  public short getValue() {
    return key.value;
  }

  public String getName() {
    return name;
  }

  public void setName(final String n) {
    name = n;
  }
}
