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

public final class SubmitLabel {

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

  public static class Key extends CompoundKey<NewRefRight.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected NewRefRight.Id newRefRightId;

    @Column(id = 2)
    protected NameKey requiredLabel;

    protected Key() {
      newRefRightId = new NewRefRight.Id();
      requiredLabel = new NameKey();
    }

    public Key(final NewRefRight.Id newRefRightId, final NameKey requiredLabel) {
      this.newRefRightId = newRefRightId;
      this.requiredLabel = requiredLabel;
    }

    @Override
    public NewRefRight.Id getParentKey() {
      return newRefRightId;
    }

    public SubmitLabel.NameKey getRequiredLabel() {
      return requiredLabel;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {requiredLabel};
    }
  }

  @Column(id = 1)
  protected Key key;

  protected SubmitLabel() {
  }

  public SubmitLabel(final NewRefRight.Id newRefRightId,
      final SubmitLabel.NameKey requiredLabel) {
    this.key = new Key(newRefRightId, requiredLabel);
  }

  public SubmitLabel.Key getKey() {
    return key;
  }
}
