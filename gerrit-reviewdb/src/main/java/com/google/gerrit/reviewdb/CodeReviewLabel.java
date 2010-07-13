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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;
import com.google.gwtorm.client.StringKey;

public final class CodeReviewLabel {

  public static class NameKey extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String name;

    protected NameKey() {
    }

    public NameKey(final String n) {
      name = n;
    }

    @Override
    public String get() {
      return name;
    }

    @Override
    protected void set(String newValue) {
      name = newValue;
    }
  }

  public static class Key extends CompoundKey<AccountGroup.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected AccountGroup.Id groupId;

    @Column(id = 2)
    protected NameKey labelToDisplay;

    protected Key() {
      groupId = new AccountGroup.Id();
      labelToDisplay = new NameKey();
    }

    public Key(final AccountGroup.Id groupId,
        final NameKey labelToDisplay) {
      this.groupId = groupId;
      this.labelToDisplay = labelToDisplay;
    }

    @Override
    public AccountGroup.Id getParentKey() {
      return groupId;
    }

    public AccountGroup.Id getAccountGroupId() {
      return groupId;
    }

    public NameKey getLabelToDisplay() {
      return labelToDisplay;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {labelToDisplay};
    }
  }

  @Column(id = 1)
  protected Key key;

  protected CodeReviewLabel() {
  }

  public CodeReviewLabel(final AccountGroup.Id groupId,
      final NameKey labelToDisplay) {
    this.key = new Key(groupId, labelToDisplay);
  }

  public CodeReviewLabel.Key getKey() {
    return key;
  }
}
