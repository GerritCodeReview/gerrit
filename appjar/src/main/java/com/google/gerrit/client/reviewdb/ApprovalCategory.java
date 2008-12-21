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
import com.google.gwtorm.client.Key;
import com.google.gwtorm.client.StringKey;

/** Types of approvals that can be associated with a {@link Change}. */
public final class ApprovalCategory {
  public static class Id extends StringKey<Key<?>> {
    @Column(length = 4)
    protected String id;

    protected Id() {
    }

    public Id(final String a) {
      id = a;
    }

    @Override
    public String get() {
      return id;
    }
  }

  @Column
  protected Id categoryId;

  @Column(length = 20)
  protected String name;

  @Column
  protected short position;

  protected ApprovalCategory() {
  }

  public ApprovalCategory(final ApprovalCategory.Id id, final String name) {
    this.categoryId = id;
    this.name = name;
  }

  public ApprovalCategory.Id getId() {
    return categoryId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String n) {
    name = n;
  }

  public short getPosition() {
    return position;
  }

  public void setPosition(final short p) {
    position = p;
  }
}
