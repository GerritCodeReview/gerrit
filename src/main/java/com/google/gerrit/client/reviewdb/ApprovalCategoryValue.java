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
import com.google.gwtorm.client.ShortKey;

/** Valid value for a {@link ApprovalCategory}. */
public final class ApprovalCategoryValue {
  public static class Id extends ShortKey<ApprovalCategory.Id> {
    @Column
    protected ApprovalCategory.Id categoryId;

    @Column
    protected short value;

    protected Id() {
      categoryId = new ApprovalCategory.Id();
    }

    public Id(final ApprovalCategory.Id cat, final short v) {
      categoryId = cat;
      value = v;
    }

    @Override
    public ApprovalCategory.Id getParentKey() {
      return categoryId;
    }

    @Override
    public short get() {
      return value;
    }

    @Override
    protected void set(short newValue) {
      value = newValue;
    }
  }

  @Column(name = Column.NONE)
  protected Id key;

  @Column(length = 50)
  protected String name;

  protected ApprovalCategoryValue() {
  }

  public ApprovalCategoryValue(final ApprovalCategoryValue.Id id,
      final String name) {
    this.key = id;
    this.name = name;
  }

  public ApprovalCategoryValue.Id getId() {
    return key;
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
