// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.ShortKey;

/** Valid value for a {@link ApprovalCategory}. */
public final class ApprovalCategoryValue {
  public static class Id extends ShortKey<ApprovalCategory.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected ApprovalCategory.Id categoryId;

    @Column(id = 2)
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

  @Column(id = 1, name = Column.NONE)
  protected Id key;

  @Column(id = 2, length = 50)
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

  public static String formatValue(short value) {
    if (value < 0) {
      return Short.toString(value);
    } else if (value == 0) {
      return " 0";
    } else {
      return "+" + Short.toString(value);
    }
  }

  public String formatValue() {
    return formatValue(getValue());
  }

  public static String format(String name, short value) {
    return new StringBuilder().append(formatValue(value))
        .append(' ').append(name).toString();
  }

  public String format() {
    return format(getName(), getValue());
  }
}
