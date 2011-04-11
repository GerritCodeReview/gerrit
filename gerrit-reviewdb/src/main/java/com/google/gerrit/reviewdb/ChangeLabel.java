// Copyright (C) 2011 The Android Open Source Project
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

public class ChangeLabel {

  public static class LabelKey extends
      StringKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected String value;

    protected LabelKey() {
    }

    public LabelKey(final String value) {
      this.value = value;
    }

    @Override
    public String get() {
      return value;
    }

    @Override
    protected void set(String value) {
      this.value = value;
    }

    public static boolean isValid(String value) {
      return value.matches("(\\-?[a-z,A-Z,0-9]+)+\\-?");
    }

    public static boolean hasOnlyValidCharacters(String value) {
      return value.matches("[a-z,A-Z,0-9,\\-]*");
    }
  }

  public static class Key extends CompoundKey<Change.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected Change.Id changeId;

    @Column(id = 2)
    protected LabelKey label;

    public Key() {
      changeId = new Change.Id();
      label = new LabelKey();
    }

    public Key(Change.Id changeId, LabelKey label) {
      this.changeId = changeId;
      this.label = label;
    }

    @Override
    public Change.Id getParentKey() {
      return changeId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {label};
    }

    public LabelKey getLabel() {
      return label;
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  public ChangeLabel() {
  }

  public ChangeLabel(Change.Id changeId, String labelValue) {
    key = new Key(changeId, new LabelKey(labelValue));
  }

  public Change.Id getChangeId() {
    return key.getParentKey();
  }

  public LabelKey getLabel() {
    return key.getLabel();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ChangeLabel) {
      final ChangeLabel b = (ChangeLabel) o;
      if (key == null) {
        if (b.key == null) {
          return true;
        } else {
          return false;
        }
      } else {
        return key.equals(b.key);
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return key.hashCode();
  }
}
